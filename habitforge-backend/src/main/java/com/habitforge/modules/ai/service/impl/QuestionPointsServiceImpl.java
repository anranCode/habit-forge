package com.habitforge.modules.ai.service.impl;

import com.habitforge.common.constant.AppConstant;
import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.common.util.RedisUtil;
import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.QuestionPointsContextAssembler;
import com.habitforge.modules.ai.client.QuestionPointsJsonParser;
import com.habitforge.modules.ai.client.QuestionPointsPromptTemplate;
import com.habitforge.modules.ai.config.AiProperties;
import com.habitforge.modules.ai.dto.QuestionPointsDraft;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.service.PlanGenerationWriter;
import com.habitforge.modules.ai.service.QuestionPointsService;
import com.habitforge.modules.study.entity.Question;
import com.habitforge.modules.study.mapper.QuestionMapper;
import com.habitforge.modules.study.service.support.PointsCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 考点识别编排（骨架逐行对照 AiScheduleServiceImpl.generate；
 * 判分链路的"空作答前置拒绝"在这里没有对应物 —— 题目本身就是输入, 无第二次前置拒绝）。
 * <p>
 * 不加事务: LLM 阻塞在事务外; 落库是单列覆盖写(updateById 全实体同值回写, 沿 QuestionServiceImpl.update 惯例)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionPointsServiceImpl implements QuestionPointsService {

    private final AiProperties aiProperties;
    private final RedisUtil redisUtil;
    private final PlanAiClient aiClient;
    private final QuestionPointsPromptTemplate promptTemplate;
    private final QuestionPointsContextAssembler contextAssembler;
    private final QuestionMapper questionMapper;
    private final PlanGenerationWriter generationWriter;

    @Override
    public void recognize(String userId, String questionId) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }

        // 归属校验在触达 LLM 之前(7007): 别人的题不烧自己的 token
        Question question = questionMapper.selectById(questionId);
        if (question == null || !userId.equals(question.getUserId())) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }

        String limitKey = QuestionPointsService.quotaKey(userId, LocalDate.now());
        if (!acquireQuota(limitKey)) {
            throw new BusinessException(ErrorCode.AI_POINTS_LIMITED);
        }

        String lockKey = "habitforge:ai:lock:points:" + userId;
        String lockOwner = UUID.randomUUID().toString();
        if (!tryLockQuietly(lockKey, lockOwner)) {
            releaseQuota(limitKey); // 未耗任何 token, 退额度
            throw new BusinessException(ErrorCode.AI_GENERATING);
        }
        try {
            PlanAiClient.ChatResult result;
            try {
                String userPrompt = contextAssembler.assemble(question) + "\n\n" + promptTemplate.formatInstructions();
                result = aiClient.chat(promptTemplate.systemPrompt(), userPrompt);
            } catch (Exception e) {
                if (e instanceof BusinessException be && be.getCode() == ErrorCode.AI_NOT_CONFIGURED.getCode()) {
                    releaseQuota(limitKey); // 未触达 LLM, 未耗 token
                    throw be;
                }
                log.warn("考点识别上游失败 user={}, question={}: {}", userId, questionId, e.getMessage());
                releaseQuota(limitKey);
                generationWriter.write(userId, null, PlanGeneration.KIND_POINTS, questionId,
                        null, false, describe(e), null);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            List<String> points;
            try {
                QuestionPointsDraft draft = promptTemplate.convert(result.content());
                points = QuestionPointsJsonParser.parse(draft,
                        aiProperties.getPointsMaxPerQuestion(), aiProperties.getPointMaxChars());
            } catch (Exception e) {
                // 解析失败 token 已耗: 不退额度, 留流水+原始输出排障
                log.warn("考点识别解析失败 user={}, question={}: {}", userId, questionId, e.getMessage());
                generationWriter.write(userId, null, PlanGeneration.KIND_POINTS, questionId,
                        result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }

            try {
                // 覆盖写: 识别结果即本题考点的新事实(与"手动编辑"共享同一列与同一编码口径)
                question.setKnowledgePoints(PointsCodec.toStored(PointsCodec.normalize(points)));
                questionMapper.updateById(question);
            } catch (Exception e) {
                log.error("考点落库失败 user={}, question={}: {}", userId, questionId, e.getMessage());
                generationWriter.write(userId, null, PlanGeneration.KIND_POINTS, questionId,
                        result, false, describe(e), result.content());
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }

            generationWriter.write(userId, null, PlanGeneration.KIND_POINTS, questionId,
                    result, true, null, result.content());
            log.info("考点识别完成 user={}, question={}, points={}", userId, questionId, points);
        } finally {
            try {
                redisUtil.unlock(lockKey, lockOwner);
            } catch (Exception e) {
                log.warn("考点识别锁释放失败(短 TTL 会自愈): {}", e.getMessage());
            }
        }
    }

    // ================= 护栏小工具（语义同 AiScheduleServiceImpl） =================

    private boolean acquireQuota(String limitKey) {
        try {
            return redisUtil.tryAcquire(limitKey, aiProperties.getPointsDailyLimit(),
                    Duration.ofHours(AppConstant.AI_RATE_WINDOW_HOURS));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("考点识别限流组件不可用, 放行: {}", e.getMessage());
            return true;
        }
    }

    private boolean tryLockQuietly(String lockKey, String owner) {
        try {
            return redisUtil.tryLock(lockKey, owner, AppConstant.AI_LOCK_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("考点识别锁组件不可用, 放行(单用户规模可接受): {}", e.getMessage());
            return true;
        }
    }

    private void releaseQuota(String limitKey) {
        try {
            redisUtil.release(limitKey);
        } catch (Exception e) {
            log.warn("考点识别额度退还失败: {}", e.getMessage());
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
