package com.habitforge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.PlanContextAssembler;
import com.habitforge.modules.ai.client.PlanJsonParser;
import com.habitforge.modules.ai.client.PlanPromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.PlanDraft;
import com.habitforge.modules.ai.dto.PlanResponse;
import com.habitforge.modules.ai.entity.DailyPlan;
import com.habitforge.modules.ai.entity.PlanBlock;
import com.habitforge.modules.ai.entity.PlanFreeSlot;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanFreeSlotMapper;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import com.habitforge.modules.ai.service.AiScheduleService;
import com.habitforge.modules.ai.service.DailyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * AI 生成编排（全程不加事务: LLM 阻塞在事务外, 落块由 DailyPlanService 单独开事务）
 * 护栏三件套: ①每日限流 tryAcquire(超限 6002, 上游失败 release 退额度, 解析失败不退)
 * ②并发锁 tryLock(冲突 6003, finally unlock) ③每次生成含失败都写 plan_generations 流水
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiScheduleServiceImpl implements AiScheduleService {

    private final AiProperties aiProperties;
    private final RedisUtil redisUtil;
    private final PlanAiClient aiClient;
    private final PlanPromptTemplate promptTemplate;
    private final PlanContextAssembler contextAssembler;
    private final DailyPlanService dailyPlanService;
    private final PlanFreeSlotMapper freeSlotMapper;
    private final PlanGenerationMapper generationMapper;

    @Override
    public PlanResponse generate(String userId, LocalDate date) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }

        // 1. 无空闲时段直接拒绝, 绝不触达 LLM（省 token）, 也不消耗额度
        DailyPlan plan = dailyPlanService.findPlan(userId, date);
        List<PlanFreeSlot> slots = plan == null ? List.of()
                : freeSlotMapper.selectList(new LambdaQueryWrapper<PlanFreeSlot>()
                        .eq(PlanFreeSlot::getPlanId, plan.getId())
                        .orderByAsc(PlanFreeSlot::getSortOrder));
        if (slots.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_FREE_SLOT_REQUIRED);
        }

        // 2. 每日限流（Redis 故障 fail-open, 沿项目惯例）
        String limitKey = "ai:plan:" + userId + ":" + date;
        boolean acquired = acquireQuota(limitKey);
        if (!acquired) {
            throw new BusinessException(ErrorCode.AI_GENERATE_LIMITED);
        }

        // 3. 并发锁防双击双份费用（owner 唯一标识本持有者, 解锁时比对 value 防误删他人锁）
        String lockKey = "habitforge:ai:lock:" + userId;
        String lockOwner = UUID.randomUUID().toString();
        boolean locked = tryLockQuietly(lockKey, lockOwner);
        if (!locked) {
            releaseQuota(limitKey); // 未耗任何 token, 退额度
            throw new BusinessException(ErrorCode.AI_GENERATING);
        }
        try {
            PlanAiClient.ChatResult result;
            PlanContextAssembler.Context context;
            try {
                context = contextAssembler.assemble(userId, date, slots);
                String userPrompt = context.userPrompt() + "\n\n" + promptTemplate.formatInstructions();
                result = aiClient.chat(promptTemplate.systemPrompt(), userPrompt);
            } catch (Exception e) {
                // 配置缺失(ChatModel/凭证不存在)原样透出 6001, 不吞成 6004
                if (e instanceof BusinessException be && be.getCode() == ErrorCode.AI_NOT_CONFIGURED.getCode()) {
                    releaseQuota(limitKey); // 未触达 LLM, 未耗 token
                    throw be;
                }
                // 上游失败（网络/鉴权/超时/模型缺失）: 退还额度 + 失败流水
                log.warn("AI 上游调用失败 user={}, date={}: {}", userId, date, e.getMessage());
                releaseQuota(limitKey);
                writeGeneration(userId, plan, null, null, null, false, describe(e), null);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            List<PlanBlock> blocks;
            try {
                PlanDraft draft = promptTemplate.convert(result.content());
                blocks = PlanJsonParser.parse(draft, context.habitIds(), context.subjectIds(), context.chapterIds(),
                        aiProperties.getMinBlocks(), aiProperties.getMaxBlocks());
            } catch (Exception e) {
                // 解析失败 token 已耗: 不退额度, 但仍留流水+原始输出排障
                log.warn("AI 返回解析失败 user={}, date={}: {}", userId, date, e.getMessage());
                writeGeneration(userId, plan, result.promptTokens(), result.completionTokens(), result.model(),
                        false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }

            try {
                dailyPlanService.replaceProposedWithGenerated(plan, blocks, result.model());
            } catch (Exception e) {
                // 落块失败 token 已耗: 同样留失败流水(带原始输出)再抛, 避免「有消耗无流水」
                log.error("AI 落块失败 user={}, date={}: {}", userId, date, e.getMessage());
                writeGeneration(userId, plan, result.promptTokens(), result.completionTokens(), result.model(),
                        false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }
            writeGeneration(userId, plan, result.promptTokens(), result.completionTokens(), result.model(),
                    true, null, result.content());
            return dailyPlanService.getPlanResponse(userId, date);
        } finally {
            try {
                redisUtil.unlock(lockKey, lockOwner);
            } catch (Exception e) {
                log.warn("AI 锁释放失败(短 TTL 会自愈): {}", e.getMessage());
            }
        }
    }

    // ================= 护栏小工具 =================

    private boolean acquireQuota(String limitKey) {
        try {
            return redisUtil.tryAcquire(limitKey, aiProperties.getDailyGenerateLimit(),
                    Duration.ofHours(AppConstant.AI_RATE_WINDOW_HOURS));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("AI 限流组件不可用, 放行: {}", e.getMessage());
            return true;
        }
    }

    private boolean tryLockQuietly(String lockKey, String owner) {
        try {
            return redisUtil.tryLock(lockKey, owner, AppConstant.AI_LOCK_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("AI 锁组件不可用, 放行(单用户规模可接受): {}", e.getMessage());
            return true;
        }
    }

    private void releaseQuota(String limitKey) {
        try {
            redisUtil.release(limitKey);
        } catch (Exception e) {
            log.warn("AI 额度退还失败: {}", e.getMessage());
        }
    }

    /** 每次生成(含失败)写流水; 流水本身失败只记日志, 不掩盖主异常 */
    private void writeGeneration(String userId, DailyPlan plan, Long promptTokens, Long completionTokens,
                                 String model, boolean success, String error, String rawOutput) {
        try {
            PlanGeneration gen = new PlanGeneration();
            gen.setUserId(userId);
            gen.setPlanId(plan == null ? null : plan.getId());
            gen.setModel(model);
            gen.setPromptTokens(promptTokens == null ? null : promptTokens.intValue());
            gen.setCompletionTokens(completionTokens == null ? null : completionTokens.intValue());
            gen.setTotalTokens((int) ((promptTokens == null ? 0L : promptTokens)
                    + (completionTokens == null ? 0L : completionTokens)));
            gen.setSuccess(success);
            gen.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
            gen.setRawOutput(rawOutput);
            generationMapper.insert(gen);
        } catch (Exception e) {
            log.error("AI 生成流水写入失败 user={}: {}", userId, e.getMessage());
        }
    }

    private static String describe(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        return msg;
    }
}
