package com.habitforge.modules.review.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.WeeklyReportContextAssembler;
import com.habitforge.modules.ai.client.WeeklyReportPromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.WeeklyReportDraft;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.review.dto.WeeklyReportResponse;
import com.habitforge.modules.review.dto.WeeklyReportUpdateRequest;
import com.habitforge.modules.review.dto.WeeklyReportUsageResponse;
import com.habitforge.modules.review.entity.Review;
import com.habitforge.modules.review.mapper.ReviewMapper;
import com.habitforge.modules.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * 周报编排（沿 AiScheduleServiceImpl 的护栏三件套, 但**不加事务**: LLM 阻塞必须在事务外）
 *
 * <p>护栏: ①每周限流 tryAcquire(超限 8002, 上游失败 release 退额度, 解析失败不退)
 * ②并发锁 tryLock(冲突 6003) ③每次生成含失败都写 plan_generations 流水(kind=WEEKLY_REPORT)
 *
 * <p>区间口径: 生成当天属于本周时, 统计止于**今天**而非周日 —— 否则未来的空白天数会把
 * "日均"摊薄, AI 会据此得出"你本周只学了 3 天"的错误结论。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final AiProperties aiProperties;
    private final RedisUtil redisUtil;
    private final PlanAiClient aiClient;
    private final WeeklyReportPromptTemplate promptTemplate;
    private final WeeklyReportContextAssembler contextAssembler;
    private final ReviewMapper reviewMapper;
    private final PlanGenerationMapper generationMapper;

    // ================= 查询 / 编辑 =================

    @Override
    public WeeklyReportResponse getWeek(String userId, LocalDate anyDayInWeek) {
        Review review = findWeek(userId, mondayOf(anyDayInWeek));
        return review == null ? null : WeeklyReportResponse.from(review);
    }

    @Override
    public WeeklyReportResponse update(String userId, String id, WeeklyReportUpdateRequest request) {
        Review review = reviewMapper.selectById(id);
        if (review == null || !review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (request.getTitle() != null) {
            review.setTitle(request.getTitle());
        }
        if (request.getScore() != null) {
            review.setScore(request.getScore());
        }
        if (request.getGoodThings() != null) {
            review.setGoodThings(request.getGoodThings());
        }
        if (request.getBadThings() != null) {
            review.setBadThings(request.getBadThings());
        }
        if (request.getLearnings() != null) {
            review.setLearnings(request.getLearnings());
        }
        if (request.getSuggestions() != null) {
            review.setSuggestions(request.getSuggestions());
        }
        reviewMapper.updateById(review);
        return WeeklyReportResponse.from(review);
    }

    @Override
    public WeeklyReportUsageResponse usage(String userId, LocalDate anyDayInWeek) {
        LocalDate start = mondayOf(anyDayInWeek);
        int limit = aiProperties.getWeeklyReportLimit();
        int used = 0;
        try {
            used = redisUtil.currentCount(ReviewService.quotaKey(userId, start));
        } catch (Exception e) {
            log.warn("周报额度读取失败(展示层降级为 0): {}", e.getMessage());
        }
        return WeeklyReportUsageResponse.builder()
                .periodStart(start)
                .periodEnd(start.plusDays(6))
                .used(used)
                .limit(limit)
                .remaining(Math.max(0, limit - used))
                .enabled(aiProperties.isEnabled())
                .build();
    }

    // ================= 生成 =================

    @Override
    public WeeklyReportResponse generate(String userId, LocalDate anyDayInWeek) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        LocalDate periodStart = mondayOf(anyDayInWeek);
        LocalDate today = LocalDate.now();
        if (periodStart.isAfter(today)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能生成未来一周的报告");
        }
        // 本周未过完则止于今天, 避免未来空白日摊薄日均
        LocalDate periodEnd = periodStart.plusDays(6);
        if (periodEnd.isAfter(today)) {
            periodEnd = today;
        }

        String limitKey = ReviewService.quotaKey(userId, periodStart);
        if (!acquireQuota(limitKey)) {
            throw new BusinessException(ErrorCode.REVIEW_WEEK_LIMITED);
        }

        String lockKey = "habitforge:ai:report:lock:" + userId;
        String lockOwner = UUID.randomUUID().toString();
        if (!tryLockQuietly(lockKey, lockOwner)) {
            releaseQuota(limitKey); // 未耗任何 token, 退额度
            throw new BusinessException(ErrorCode.AI_GENERATING);
        }
        try {
            WeeklyReportContextAssembler.Context context;
            PlanAiClient.ChatResult result;
            try {
                context = contextAssembler.assemble(userId, periodStart, periodEnd);
                String userPrompt = context.userPrompt() + "\n\n" + promptTemplate.formatInstructions();
                result = aiClient.chat(promptTemplate.systemPrompt(), userPrompt);
            } catch (Exception e) {
                // 配置缺失原样透出 6001, 不吞成 6004
                if (e instanceof BusinessException be && be.getCode() == ErrorCode.AI_NOT_CONFIGURED.getCode()) {
                    releaseQuota(limitKey);
                    throw be;
                }
                log.warn("周报 AI 调用失败 user={}, period={} ~ {}: {}", userId, periodStart, periodEnd, e.getMessage());
                releaseQuota(limitKey);
                writeGeneration(userId, null, false, describe(e), null);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            WeeklyReportDraft draft;
            List<String> suggestions;
            try {
                draft = promptTemplate.convert(result.content());
                suggestions = cleanSuggestions(draft);
                if (suggestions.size() < aiProperties.getReportMinSuggestions()) {
                    throw new IllegalStateException("建议条数不足: " + suggestions.size());
                }
            } catch (Exception e) {
                // 解析失败 token 已耗: 不退额度, 留失败流水与原始输出排障
                log.warn("周报解析失败 user={}, period={}: {}", userId, periodStart, e.getMessage());
                writeGeneration(userId, result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }

            Review saved;
            try {
                saved = persist(userId, periodStart, periodEnd, draft, suggestions, context.snapshotJson(), result);
            } catch (Exception e) {
                log.error("周报落库失败 user={}, period={}: {}", userId, periodStart, e.getMessage());
                writeGeneration(userId, result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }
            writeGeneration(userId, result, true, null, result.content());
            log.info("周报生成成功 user={}, period={} ~ {}, score={}", userId, periodStart, periodEnd, saved.getScore());
            return WeeklyReportResponse.from(saved);
        } finally {
            try {
                redisUtil.unlock(lockKey, lockOwner);
            } catch (Exception e) {
                log.warn("周报锁释放失败(短 TTL 会自愈): {}", e.getMessage());
            }
        }
    }

    // ================= 内部 =================

    /** 一人一周一行: 存在则覆盖（重新生成 = 刷新同一份报告, 不产生第二行） */
    private Review persist(String userId, LocalDate periodStart, LocalDate periodEnd, WeeklyReportDraft draft,
                           List<String> suggestions, String snapshotJson, PlanAiClient.ChatResult result) {
        Review existing = findWeek(userId, periodStart);
        Review target = existing != null ? existing : new Review();
        target.setUserId(userId);
        target.setType(Review.TYPE_WEEKLY);
        target.setPeriodStart(periodStart);
        target.setPeriodEnd(periodEnd);
        target.setReviewDate(LocalDate.now());
        target.setTitle(truncate(draft.title(), 200));
        target.setScore(clampScore(draft.score()));
        target.setGoodThings(draft.goodThings());
        target.setBadThings(draft.badThings());
        target.setLearnings(draft.learnings());
        target.setSuggestions(String.join("\n", suggestions));
        target.setStatsSnapshot(snapshotJson);
        target.setAiGenerated(true);
        target.setModel(result.model());
        target.setTotalTokens((int) result.totalTokens());
        try {
            if (existing != null) {
                reviewMapper.updateById(target);
            } else {
                reviewMapper.insert(target);
            }
        } catch (DuplicateKeyException e) {
            // 并发下唯一键兜底: 改走更新（锁失效时的最后一道防线）
            Review again = findWeek(userId, periodStart);
            if (again == null) {
                throw e;
            }
            target.setId(again.getId());
            reviewMapper.updateById(target);
        }
        return target;
    }

    private Review findWeek(String userId, LocalDate periodStart) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getType, Review.TYPE_WEEKLY)
                .eq(Review::getPeriodStart, periodStart)
                .last("LIMIT 1"));
    }

    private static List<String> cleanSuggestions(WeeklyReportDraft draft) {
        if (draft == null || draft.suggestions() == null) {
            return List.of();
        }
        return draft.suggestions().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::strip)
                .toList();
    }

    private static Integer clampScore(Integer score) {
        if (score == null) {
            return null;
        }
        return Math.max(0, Math.min(100, score));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** 该周周一（ISO 周: 周一为一周之始） */
    private static LocalDate mondayOf(LocalDate anyDay) {
        LocalDate base = anyDay != null ? anyDay : LocalDate.now();
        return base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean acquireQuota(String limitKey) {
        try {
            return redisUtil.tryAcquire(limitKey, aiProperties.getWeeklyReportLimit(),
                    Duration.ofDays(AppConstant.AI_REPORT_RATE_WINDOW_DAYS));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("周报限流组件不可用, 放行: {}", e.getMessage());
            return true;
        }
    }

    private boolean tryLockQuietly(String lockKey, String owner) {
        try {
            return redisUtil.tryLock(lockKey, owner, AppConstant.AI_LOCK_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("周报锁组件不可用, 放行(单用户规模可接受): {}", e.getMessage());
            return true;
        }
    }

    private void releaseQuota(String limitKey) {
        try {
            redisUtil.release(limitKey);
        } catch (Exception e) {
            log.warn("周报额度退还失败: {}", e.getMessage());
        }
    }

    /** 每次生成（含失败）写流水; 流水失败只记日志, 不掩盖主异常 */
    private void writeGeneration(String userId, PlanAiClient.ChatResult result, boolean success,
                                 String error, String rawOutput) {
        try {
            PlanGeneration gen = new PlanGeneration();
            gen.setUserId(userId);
            gen.setPlanId(null);
            gen.setKind(PlanGeneration.KIND_WEEKLY_REPORT);
            gen.setModel(result == null ? null : result.model());
            gen.setPromptTokens(result == null || result.promptTokens() == null ? null : result.promptTokens().intValue());
            gen.setCompletionTokens(result == null || result.completionTokens() == null ? null : result.completionTokens().intValue());
            gen.setTotalTokens(result == null ? 0 : (int) result.totalTokens());
            gen.setSuccess(success);
            gen.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
            gen.setRawOutput(rawOutput);
            generationMapper.insert(gen);
        } catch (Exception e) {
            log.error("周报生成流水写入失败 user={}: {}", userId, e.getMessage());
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
