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
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
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

    /** 只用于判断"key 到底配没配"; 可能不存在(非 anthropic 供应商/未装配), 故走 ObjectProvider */
    private final ObjectProvider<AnthropicConnectionProperties> connectionProperties;

    public PlanAiClient(ObjectProvider<ChatModel> chatModelProvider,
                        ObjectProvider<AnthropicConnectionProperties> connectionProperties) {
        this.chatModelProvider = chatModelProvider;
        this.connectionProperties = connectionProperties;
    }

    /**
     * @throws BusinessException AI_NOT_CONFIGURED — 凭证未配置; AI_SERVICE_ERROR — ChatModel 缺失或上游异常,
     *         语义由调用方统一翻译 6004
     */
    public ChatResult chat(String systemPrompt, String userPrompt) {
        if (credentialsMissing()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        // 直接传 Prompt 消息而非 .user(text): 避免 prompt 文本(含 JSON Schema 花括号)被 StringTemplate 再渲染
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
        ChatResponse response = ChatClient.create(model).prompt(prompt).call().chatResponse();
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 返回空响应");
        }
        String content = extractContent(response);
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String modelName = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new ChatResult(
                content,
                usage == null ? null : toLong(usage.getPromptTokens()),
                usage == null ? null : toLong(usage.getCompletionTokens()),
                modelName == null || modelName.isBlank() ? "unknown" : modelName);
    }

    /**
     * 凭证是否根本没配上 —— 为什么必须单独判一次：
     * spring-ai 的 AnthropicChatModel 在 key 为空时**不会**构造失败（已核对 AnthropicApi 字节码：
     * 只是 `if (StringUtils.hasText(apiKey)) headers.add("x-api-key", ...)`, 没有 Assert），
     * 于是 starter 在场时 ChatModel bean 永远存在，请求会带着**缺失/假的** x-api-key 发出去，
     * 上游回 401 → 调用方翻译成 6004「调用失败」。用户看到的是"服务坏了"，真相是"压根没配"。
     * 这里把它提前收敛成 6001「AI 服务未启用」，与文档/app.ai.enabled 的语义一致。
     *
     * 注意 properties 为空时返回 false（不拦）：那说明当前不是 anthropic 供应商或未装配,
     * 判断权交回 ChatModel 本身, 避免把别的供应商误判成"未配置"。
     */
    private boolean credentialsMissing() {
        AnthropicConnectionProperties props = connectionProperties.getIfAvailable();
        if (props == null) {
            return false;
        }
        String apiKey = props.getApiKey();
        // ${ 开头 = 未被解析的占位符字面量(yml 里写了 ${AI_API_KEY} 却没默认值), 等同没配
        return apiKey == null || apiKey.isBlank() || apiKey.startsWith("${");
    }

    /**
     * 取正文：spring-ai 1.1.8 的 AnthropicChatModel 会为响应里**每个 content 块各生成一个 Generation**
     * （已核对 AnthropicChatModel#toChatResponse 字节码：全量遍历 content 逐个 List.add），
     * 而 ChatResponse#getResult() 只返回 generations.get(0)。
     * 上游若返回 thinking 块（Anthropic 协议里排在 text 之前），第 0 个就是思考文本 ——
     * 直接取 getResult() 会把思考当 JSON 去反序列化，必然误报 6005。
     * 故取最后一个非空 Generation：text 块总在 thinking 之后，关闭 thinking 时只有一个 Generation，二者等价。
     * 配置侧已强制 thinking=disabled，此处是防配置回退/换供应商后静默坏掉的兜底。
     */
    private static String extractContent(ChatResponse response) {
        List<Generation> results = response.getResults();
        if (results.size() > 1) {
            log.warn("AI 返回 {} 个 Generation(预期 1 个, 疑似 thinking 未关闭), 取最后一个非空作为正文",
                    results.size());
        }
        for (int i = results.size() - 1; i >= 0; i--) {
            Generation g = results.get(i);
            if (g != null && g.getOutput() != null) {
                String text = g.getOutput().getText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 返回空响应");
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
