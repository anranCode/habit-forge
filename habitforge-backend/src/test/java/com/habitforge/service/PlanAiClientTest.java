package com.habitforge.service;

import com.habitforge.common.exception.BusinessException;
import com.habitforge.common.exception.ErrorCode;
import com.habitforge.modules.ai.client.PlanAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 正文抽取的回归护栏：AnthropicChatModel 为响应里每个 content 块各生成一个 Generation，
 * getResult() 只取第 0 个 —— 上游返回 thinking 块(排在 text 之前)时第 0 个是思考文本。
 * PlanAiClient 取最后一个非空 Generation 作为正文。全程 mock，不打真实端点。
 */
class PlanAiClientTest {

    private static Generation gen(String text) {
        return new Generation(new AssistantMessage(text));
    }

    private static ChatResponse response(Generation... generations) {
        return new ChatResponse(List.of(generations), ChatResponseMetadata.builder()
                .model("deepseek-v4-flash")
                .usage(new DefaultUsage(11, 22))
                .build());
    }

    private static PlanAiClient clientWith(ChatResponse response) {
        return clientWith(response, null);
    }

    /** props=null 模拟"非 anthropic 供应商/未装配"，此时凭证护栏必须放行，判断权交回 ChatModel */
    private static PlanAiClient clientWith(ChatResponse response, AnthropicConnectionProperties props) {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response;
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        @SuppressWarnings("unchecked")
        ObjectProvider<AnthropicConnectionProperties> conn = mock(ObjectProvider.class);
        when(conn.getIfAvailable()).thenReturn(props);
        return new PlanAiClient(provider, conn);
    }

    private static AnthropicConnectionProperties props(String apiKey) {
        AnthropicConnectionProperties p = new AnthropicConnectionProperties();
        p.setApiKey(apiKey);
        return p;
    }

    @Test
    void thinkingGenerationBeforeTextIsNotMistakenForContent() {
        PlanAiClient client = clientWith(response(gen("让我先想想该怎么排……"), gen("{\"blocks\":[]}")));
        assertEquals("{\"blocks\":[]}", client.chat("system", "user").content(),
                "thinking 块在 text 之前，必须取到 text 而不是思考文本");
    }

    @Test
    void singleTextGenerationStillWorks() {
        assertEquals("{\"blocks\":[]}", clientWith(response(gen("{\"blocks\":[]}"))).chat("s", "u").content());
    }

    @Test
    void skipsBlankLeadingGenerationInFavourOfLaterText() {
        PlanAiClient client = clientWith(response(gen(""), gen("{\"blocks\":[]}")));
        assertEquals("{\"blocks\":[]}", client.chat("s", "u").content());
    }

    @Test
    void mapsUsageTokensAndModelName() {
        PlanAiClient.ChatResult result = clientWith(response(gen("{}"))).chat("s", "u");
        assertEquals(11L, result.promptTokens());
        assertEquals(22L, result.completionTokens());
        assertEquals(33L, result.totalTokens());
        assertEquals("deepseek-v4-flash", result.model());
    }

    @Test
    void emptyOrBlankResponseIsServiceError() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> clientWith(response()).chat("s", "u"));
        assertEquals(ErrorCode.AI_SERVICE_ERROR.getCode(), empty.getCode());

        BusinessException blank = assertThrows(BusinessException.class,
                () -> clientWith(response(gen("   "))).chat("s", "u"));
        assertEquals(ErrorCode.AI_SERVICE_ERROR.getCode(), blank.getCode());
    }

    // ---------- 凭证护栏：没配 key 时必须是 6001(未启用) 而不是发出去撞 401 变 6004 ----------

    @Test
    void blankApiKeyIsNotConfiguredNotServiceError() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> clientWith(response(gen("{}")), props("")).chat("s", "u"));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED.getCode(), e.getCode(),
                "空 key 应报 6001「未启用」：AnthropicApi 不会因空 key 构造失败，请求会带缺失的 x-api-key 发出去撞 401");
    }

    @Test
    void unresolvedPlaceholderApiKeyIsNotConfigured() {
        // application-prod.yml 若写成 ${AI_API_KEY}(无默认值), Boot 会原样绑成这个字面量
        BusinessException e = assertThrows(BusinessException.class,
                () -> clientWith(response(gen("{}")), props("${AI_API_KEY}")).chat("s", "u"));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED.getCode(), e.getCode());
    }

    @Test
    void configuredApiKeyProceedsAndAbsentPropertiesDoNotBlock() {
        // 配了 key: 正常走到解析逻辑（此处正文是 {} 之后照常返回）
        assertEquals("{}", clientWith(response(gen("{}")), props("sk-real-key")).chat("s", "u").content());
        // properties 缺失(非 anthropic 供应商): 不得拦截, 否则会误判成未配置
        assertEquals("{}", clientWith(response(gen("{}")), null).chat("s", "u").content());
    }
}
