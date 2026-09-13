package com.habitforge.service;

import com.habitforge.modules.ai.client.PlanAiClient;
import com.habitforge.modules.ai.client.PlanJsonParser;
import com.habitforge.modules.ai.client.PlanPromptTemplate;
import com.habitforge.modules.ai.dto.PlanDraft;
import com.habitforge.modules.ai.entity.PlanBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 真实供应商联调（**默认跳过**，只有显式设置 AI_LIVE_TEST=1 且提供 AI_API_KEY 才运行）。
 *
 * 为什么单独成一条：单测全程 mock ChatModel，无法覆盖三件只有真调用才暴露的事 ——
 *  ① 鉴权头与默认头(spring-ai 会额外发 anthropic-beta)在目标端点上是否被接受；
 *  ② thinking:disabled 是否真的透传到 API（判据：响应只有 1 个 Generation，没有 thinking 块）；
 *  ③ 真实返回是否仍能被 BeanOutputConverter + PlanJsonParser 走通。
 *
 * 运行方式（key 只从环境变量来，绝不写进命令文本或代码）：
 *   AI_LIVE_TEST=1 AI_API_KEY=... mvn -Dtest=AiProviderLiveTest test
 *
 * 构造的 options 与 application.yml 保持一致（由 AiThinkingConfigTest 断言其一致性）。
 */
@EnabledIfEnvironmentVariable(named = "AI_LIVE_TEST", matches = "1")
class AiProviderLiveTest {

    private static final String BASE_URL = "https://api.deepseek.com/anthropic";
    private static final String MODEL = "deepseek-v4-flash";

    /** 包一层记录最后一次响应，好让断言既能看 Generation 结构、又能走 PlanAiClient 的抽取逻辑 */
    private static final class RecordingChatModel implements ChatModel {
        private final ChatModel delegate;
        private ChatResponse last;

        private RecordingChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.last = delegate.call(prompt);
            return this.last;
        }
    }

    @Test
    void realCallIsAcceptedAndYieldsParsablePlanWithoutThinkingBlocks() {
        String apiKey = System.getenv("AI_API_KEY");
        assertNotNull(apiKey, "AI_LIVE_TEST=1 时必须提供 AI_API_KEY");

        AnthropicApi api = AnthropicApi.builder()
                .baseUrl(BASE_URL)
                .apiKey(apiKey)
                .restClientBuilder(RestClient.builder())
                .webClientBuilder(WebClient.builder())
                .build();
        AnthropicChatModel model = AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model(MODEL)
                        .temperature(0.5)
                        .maxTokens(4096)
                        .thinking(AnthropicApi.ThinkingType.DISABLED, null)
                        .build())
                .build();

        RecordingChatModel recording = new RecordingChatModel(model);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recording);
        // 真实绑定 spring.ai.anthropic.api-key 的形态：key 有值 => 凭证护栏放行, 走真实调用
        AnthropicConnectionProperties props = new AnthropicConnectionProperties();
        props.setApiKey(apiKey);
        props.setBaseUrl(BASE_URL);
        @SuppressWarnings("unchecked")
        ObjectProvider<AnthropicConnectionProperties> connProvider = mock(ObjectProvider.class);
        when(connProvider.getIfAvailable()).thenReturn(props);
        PlanAiClient client = new PlanAiClient(provider, connProvider);

        String habitId = UUID.randomUUID().toString();
        String subjectId = UUID.randomUUID().toString();
        String chapterId = UUID.randomUUID().toString();

        PlanPromptTemplate template = new PlanPromptTemplate();
        String userPrompt = """
                ## 今天
                2026-09-13 星期日

                ## 今日空闲时段（时间块只能排在以下区间内）
                - 07:00-09:00（早起）
                - 19:00-22:00（晚间）

                ## 今日待打卡习惯（habitId 只能从这里挑选）
                - habitId=%s | 晨跑 | 惯常执行时间=07:00 | 分类=健康 | 已连续12天
                - habitId=%s | 阅读30分钟 | 惯常执行时间=21:30 | 分类=成长

                ## 学习任务（subjectId/chapterId 只能从这里挑选）
                - subjectId=%s | 高等数学(一) | 距考试23天 | 章节进度 4/12 | 到期闪卡18张
                    章节 chapterId=%s | 第三章 导数与微分 | 进行中

                ## 近期日记（心情与状态的信号）
                - 2026-09-12 心情=疲惫：今天上班太累了，晚上只看了半小时书就睡着了。
                """.formatted(habitId, UUID.randomUUID(), subjectId, chapterId)
                + "\n" + template.formatInstructions();

        PlanAiClient.ChatResult result = client.chat(template.systemPrompt(), userPrompt);

        // ② thinking 是否真的关掉：关掉后只有 text 块 → 恰好 1 个 Generation
        assertEquals(1, recording.last.getResults().size(),
                "响应出现了多个 Generation，说明 thinking 未被关闭（thinking 块会排在 text 之前）");
        assertFalse(recording.last.getResults().get(0).getOutput().getText().isBlank(), "正文为空");

        // ① 头被接受：能拿到 token 用量即说明请求被正常处理
        assertNotNull(result.promptTokens(), "未取到 prompt token 用量");
        assertNotNull(result.completionTokens(), "未取到 completion token 用量");

        // ③ 真实输出必须能走完「结构化转换 + 业务校验」漏斗
        assertFalse(result.content().contains("```"), "返回里不应出现 Markdown 围栏");
        PlanDraft draft = template.convert(result.content());
        List<PlanBlock> blocks = PlanJsonParser.parse(draft,
                Set.of(habitId), Set.of(subjectId), Set.of(chapterId), 3, 12);
        assertTrue(blocks.size() >= 3, "有效块不足 3 个：" + blocks.size());
        assertTrue(blocks.stream().noneMatch(b -> b.getHabitId() != null && !habitId.equals(b.getHabitId())),
                "出现了白名单外的 habitId（幻觉 ID 未被清空）");
        assertTrue(blocks.stream().noneMatch(b -> b.getChapterId() != null && !chapterId.equals(b.getChapterId())),
                "出现了白名单外的 chapterId（幻觉 ID 未被清空）");
    }
}
