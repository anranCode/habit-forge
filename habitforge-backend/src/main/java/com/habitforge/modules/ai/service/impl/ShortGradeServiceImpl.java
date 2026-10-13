package com.habitforge.modules.ai.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.ShortGradeContextAssembler;
import com.habitforge.modules.ai.client.ShortGradeJsonParser;
import com.habitforge.modules.ai.client.ShortGradePromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.ShortGradeDraft;
import com.habitforge.modules.ai.dto.ShortGradeOutcome;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.service.PlanGenerationWriter;
import com.habitforge.modules.ai.service.ShortGradeService;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.entity.QuestionGrade;
import com.habitforge.modules.study.mapper.QuestionGradeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 主观题判分编排（骨架逐行对照 AiScheduleServiceImpl.generate: enabled → 空作答前置拒绝 →
 * 限流 → 锁 → 调用 → 解析 → 留痕 → 流水; 语义细则见接口注释）。
 * <p>
 * 全程不加事务: LLM 阻塞绝不能捆住连接; question_grades 是单行 insert, 原子性由 DB 天然保证。
 * 锁/限流键独立于 PLAN 流 —— 排程生成阻塞 120s 期间不应卡住判分（反之亦然）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortGradeServiceImpl implements ShortGradeService {

    private final AiProperties aiProperties;
    private final RedisUtil redisUtil;
    private final PlanAiClient aiClient;
    private final ShortGradePromptTemplate promptTemplate;
    private final ShortGradeContextAssembler contextAssembler;
    private final QuestionGradeMapper questionGradeMapper;
    private final PlanGenerationWriter generationWriter;

    @Override
    public ShortGradeOutcome grade(String userId, Question question, String userAnswer) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }

        // 1. 空作答直接拒绝, 绝不触达 LLM（省 token）, 也不消耗额度
        //    （GradeRequest @NotBlank 已挡外层, 这里是对所有调用方的兜底承诺）
        String answer = userAnswer == null ? "" : userAnswer.strip();
        if (answer.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先写下作答，再交给 AI 判分");
        }

        // 2. 每日判分限流（Redis 故障 fail-open, 沿项目惯例）
        String limitKey = ShortGradeService.quotaKey(userId, LocalDate.now());
        if (!acquireQuota(limitKey)) {
            throw new BusinessException(ErrorCode.AI_GRADE_LIMITED);
        }

        // 3. 判卷锁防双击双份费用（与排程锁分键, 互不阻塞）
        String lockKey = "habitforge:ai:lock:grade:" + userId;
        String lockOwner = UUID.randomUUID().toString();
        if (!tryLockQuietly(lockKey, lockOwner)) {
            releaseQuota(limitKey); // 未耗任何 token, 退额度
            throw new BusinessException(ErrorCode.AI_GENERATING);
        }
        try {
            PlanAiClient.ChatResult result;
            try {
                String userPrompt = contextAssembler.assemble(question, answer) + "\n\n" + promptTemplate.formatInstructions();
                result = aiClient.chat(promptTemplate.systemPrompt(), userPrompt);
            } catch (Exception e) {
                // 配置缺失原样透出 6001, 不吞成 6004（调用方错题本副作用以此为"未判分"信号）
                if (e instanceof BusinessException be && be.getCode() == ErrorCode.AI_NOT_CONFIGURED.getCode()) {
                    releaseQuota(limitKey); // 未触达 LLM, 未耗 token
                    throw be;
                }
                log.warn("判分 AI 上游调用失败 user={}, question={}: {}", userId, question.getId(), e.getMessage());
                releaseQuota(limitKey);
                generationWriter.write(userId, null, PlanGeneration.KIND_GRADE, question.getId(),
                        null, false, describe(e), null);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            List<ShortGradeDraft.PointDraft> points;
            try {
                ShortGradeDraft draft = promptTemplate.convert(result.content());
                points = ShortGradeJsonParser.parse(draft);
            } catch (Exception e) {
                // 解析失败 token 已耗: 不退额度, 但留流水+原始输出排障
                log.warn("判分 AI 返回解析失败 user={}, question={}: {}", userId, question.getId(), e.getMessage());
                generationWriter.write(userId, null, PlanGeneration.KIND_GRADE, question.getId(),
                        result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }

            int covered = ShortGradeJsonParser.countCovered(points);
            int total = points.size();
            boolean correct = covered >= Math.ceil(total * AppConstant.SHORT_GRADE_PASS_RATIO);
            double ratio = Math.round(covered * 100.0 / total) / 100.0;

            QuestionGrade row;
            try {
                row = new QuestionGrade();
                row.setUserId(userId);
                row.setQuestionId(question.getId());
                row.setUserAnswer(truncate(answer, AppConstant.SHORT_GRADE_ANSWER_MAX_CHARS));
                row.setCorrect(correct);
                row.setPoints(toStoredJson(points));
                row.setCovered(covered);
                row.setTotal(total);
                row.setRatio(ratio);
                row.setModel(result.model());
                row.setTotalTokens((int) result.totalTokens());
                questionGradeMapper.insert(row);
            } catch (Exception e) {
                // 留痕失败 token 已耗: 同样失败流水+6004, 不留「有消耗无流水」缺口（沿落块失败语义）
                log.error("判分留痕写入失败 user={}, question={}: {}", userId, question.getId(), e.getMessage());
                generationWriter.write(userId, null, PlanGeneration.KIND_GRADE, question.getId(),
                        result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            generationWriter.write(userId, null, PlanGeneration.KIND_GRADE, question.getId(),
                    result, true, null, result.content());
            log.info("判分完成 user={}, question={}, covered={}/{}, correct={}", userId, question.getId(),
                    covered, total, correct);
            return new ShortGradeOutcome(points, covered, total, ratio, correct, row.getId());
        } finally {
            try {
                redisUtil.unlock(lockKey, lockOwner);
            } catch (Exception e) {
                log.warn("判分锁释放失败(短 TTL 会自愈): {}", e.getMessage());
            }
        }
    }

    // ================= 护栏小工具（语义同 AiScheduleServiceImpl, 文案与日志主语不同） =================

    private boolean acquireQuota(String limitKey) {
        try {
            return redisUtil.tryAcquire(limitKey, aiProperties.getGradeDailyLimit(),
                    Duration.ofHours(AppConstant.AI_RATE_WINDOW_HOURS));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("判分限流组件不可用, 放行: {}", e.getMessage());
            return true;
        }
    }

    private boolean tryLockQuietly(String lockKey, String owner) {
        try {
            return redisUtil.tryLock(lockKey, owner, AppConstant.AI_LOCK_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("判分锁组件不可用, 放行(单用户规模可接受): {}", e.getMessage());
            return true;
        }
    }

    private void releaseQuota(String limitKey) {
        try {
            redisUtil.release(limitKey);
        } catch (Exception e) {
            log.warn("判分额度退还失败: {}", e.getMessage());
        }
    }

    /** 要点清单 → [{"point","hit"}]（record 无 bean getter, 手动组 JSON 不赌 hutool 版本对 record 的支持） */
    private static String toStoredJson(List<ShortGradeDraft.PointDraft> points) {
        JSONArray arr = new JSONArray();
        for (ShortGradeDraft.PointDraft p : points) {
            JSONObject o = new JSONObject();
            o.set("point", p.point());
            o.set("hit", p.hit());
            arr.add(o);
        }
        return arr.toString();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
