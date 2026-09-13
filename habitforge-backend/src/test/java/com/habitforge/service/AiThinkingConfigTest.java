package com.habitforge.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 供应商配置级护栏（DeepSeek Anthropic 兼容端点）。
 *
 * 为什么必须有这条测试：spring-ai 1.1.8 的 AnthropicChatModel#toChatResponse 会为响应里
 * **每个 content 块各生成一个 Generation**（全量遍历 content 逐个 List.add），而
 * ChatResponse#getResult() 只取 generations.get(0)。Anthropic 协议里 thinking 块排在 text 之前，
 * 因此不关思考时取到的正文是**思考文本**，BeanOutputConverter 反序列化必然失败 —— 每次生成都误报 6005。
 * 另外思考 token 计入 max_tokens，实测最重上下文思考可涨到 17k 字符、吃尽 8000 全部额度导致正文被截断。
 *
 * 绑定路径与运行时一致（Spring Binder + 真实 application.yml）；spring-configuration-metadata.json
 * 里只有整条 spring.ai.anthropic.chat.options.thinking、没有 .type 子属性条目，
 * 嵌套 record 能否绑上必须实测，所以这里断言的是**绑定结果**而不是 yml 里的字面值。
 */
class AiThinkingConfigTest {

    /** 真实 application.yml 装进 Environment —— 走 Environment 才能解析 ${AI_MODEL:...} 这类占位符 */
    private static MockEnvironment envWithYaml() throws IOException {
        MockEnvironment env = new MockEnvironment();
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        for (PropertySource<?> ps : sources) {
            env.getPropertySources().addFirst(ps);
        }
        return env;
    }

    private static AnthropicChatOptions boundOptions(MockEnvironment env) {
        return Binder.get(env)
                .bind(AnthropicChatProperties.CONFIG_PREFIX, Bindable.of(AnthropicChatProperties.class))
                .orElseThrow(() -> new AssertionError("spring.ai.anthropic.chat 绑定失败"))
                .getOptions();
    }

    @Test
    void thinkingMustBeDisabled() throws IOException {
        AnthropicChatOptions options = boundOptions(envWithYaml());
        assertNotNull(options.getThinking(),
                "必须显式配置 spring.ai.anthropic.chat.options.thinking.type=disabled："
                        + "上游返回 thinking 块时 getResult() 取到的是思考文本，JSON 解析必然失败(6005)");
        assertEquals(AnthropicApi.ThinkingType.DISABLED, options.getThinking().type(),
                "thinking 必须是 disabled");
        assertNull(options.getThinking().budgetTokens(), "关闭思考时不应再传 budget_tokens");
    }

    @Test
    void pointedAtDeepSeekWithEnoughOutputHeadroom() throws IOException {
        MockEnvironment env = envWithYaml();
        assertEquals("https://api.deepseek.com/anthropic", env.getProperty("spring.ai.anthropic.base-url"));

        AnthropicChatOptions options = boundOptions(env);
        assertEquals("deepseek-v4-flash", options.getModel());
        assertEquals(0.5d, options.getTemperature());
        assertNotNull(options.getMaxTokens(), "max-tokens 必须显式配置");
        assertTrue(options.getMaxTokens() >= 4096,
                "max-tokens 需 ≥4096：实测最重上下文(8 空闲段+6 习惯+5 章节)输出 ≈1.3k tokens，"
                        + "上限过小会导致 JSON 被截断断尾(6005)");
    }
}
