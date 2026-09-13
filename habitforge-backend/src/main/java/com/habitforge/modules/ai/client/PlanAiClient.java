package com.habitforge.modules.ai.client;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ChatClient 薄封装（单轮 system+user 同步调用; token 用量取 ChatResponse.getMetadata().getUsage()）
 * 用 ObjectProvider 延迟解析 ChatModel: AI 未启用/凭证缺失不影响应用启动
 */
@Slf4j
@Component
public class PlanAiClient {

    private final ObjectProvider<ChatModel> chatModelProvider;

    public PlanAiClient(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    /**
     * @throws BusinessException AI_SERVICE_ERROR — ChatModel 缺失或上游异常语义由调用方统一翻译 6004
     */
    public ChatResult chat(String systemPrompt, String userPrompt) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        // 直接传 Prompt 消息而非 .user(text): 避免 prompt 文本(含 JSON Schema 花括号)被 StringTemplate 再渲染
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        ChatResponse response = ChatClient.create(model).prompt(prompt).call().chatResponse();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 返回空响应");
        }
        String content = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String modelName = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatResult(
                content,
                usage == null ? null : toLong(usage.getPromptTokens()),
                usage == null ? null : toLong(usage.getCompletionTokens()),
                modelName == null || modelName.isBlank() ? "unknown" : modelName);
    }

    /** Spring AI Usage 各版本 Integer/Long 返回值不一, 统一升为 Long */
    private static Long toLong(Number n) {
        return n == null ? null : n.longValue();
    }

    /** 单轮调用结果（token 计数用 Long, 与 Spring AI Usage 接口一致） */
    public record ChatResult(String content, Long promptTokens, Long completionTokens, String model) {

        public long totalTokens() {
            return (promptTokens == null ? 0L : promptTokens) + (completionTokens == null ? 0L : completionTokens);
        }
    }
}
