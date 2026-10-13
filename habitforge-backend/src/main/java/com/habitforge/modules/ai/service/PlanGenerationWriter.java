package com.habitforge.modules.ai.service;

import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.entity.PlanGeneration;
import com.habitforge.modules.ai.mapper.PlanGenerationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 生成流水写手（每次调用含失败都落一行 plan_generations）。
 * <p>
 * 从 AiScheduleServiceImpl / ReviewServiceImpl 两私有方法下沉:
 * 四个 AI 场景(PLAN/WEEKLY_REPORT/POINTS/GRADE)共用同一张流水表,
 * 再复制第三、四份就会有人在"错误截断/token 换算"上抄漏。
 * 流水本身失败只记日志, 绝不掩盖调用方的主异常(沿两处原语义)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanGenerationWriter {

    private final PlanGenerationMapper generationMapper;

    /**
     * @param planId 今日安排流的目标计划 id, 其余流传 null
     * @param kind   PlanGeneration.KIND_* 四常量之一
     * @param refId  POINTS/GRADE 流的 question_id, 其余传 null
     * @param result 已触达 LLM 时传 ChatResult(带 token 用量); 未触达传 null(全列留空)
     * @param error  失败原因摘要, 落库前截 500
     */
    public void write(String userId, String planId, String kind, String refId,
                      PlanAiClient.ChatResult result, boolean success, String error, String rawOutput) {
        try {
            PlanGeneration gen = new PlanGeneration();
            gen.setUserId(userId);
            gen.setPlanId(planId);
            gen.setKind(kind);
            gen.setRefId(refId);
            gen.setModel(result == null ? null : result.model());
            gen.setPromptTokens(result == null || result.promptTokens() == null ? null : result.promptTokens().intValue());
            gen.setCompletionTokens(result == null || result.completionTokens() == null ? null : result.completionTokens().intValue());
            gen.setTotalTokens(result == null ? 0 : (int) result.totalTokens());
            gen.setSuccess(success);
            gen.setError(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
            gen.setRawOutput(rawOutput);
            generationMapper.insert(gen);
        } catch (Exception e) {
            log.error("AI 流水写入失败 user={}, kind={}: {}", userId, kind, e.getMessage());
        }
    }
}
