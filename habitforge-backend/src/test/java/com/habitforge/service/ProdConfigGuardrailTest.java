package com.habitforge.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产配置级护栏（application-prod.yml）。
 *
 * 这两条都对应「配置写了但等于没写」的真实事故类型——都在 2026-09-13 的部署就绪审计中被确认过，
 * 且共同特征是**不会导致启动失败、冒烟检查全绿**，只有真正用了才发现：
 *
 * ① `spring.data.redis.timeout` 缺失 → Lettuce 用默认 **60s** 命令超时。Redis 进程挂掉会立即拒绝
 *    连接、fail-open 照常生效；但 6379 被安全组 DROP/网络悬挂时 TCP 会**悬挂**，于是每个已登录
 *    请求都要先卡满 60s 才走进 catch 里的 fail-open（JWT 过滤器每个请求都查一次黑名单，
 *    打卡/日记/上传/AI 还各有一次限流）。前端 axios 默认只等 15s，用户看到的是「网络异常」，
 *    而后端 healthcheck 只探 8080 端口、容器仍 healthy —— 表现成「站点能打开、点任何按钮都
 *    转圈失败」，排查时根本不会指向 Redis。dev 有 5s，prod 原先什么都没有。
 *
 * ② `spring.ai.anthropic.api-key` 的占位符必须带空默认值。Boot 3.5 的
 *    PropertySourcesPlaceholdersResolver 是 `ignoreUnresolvablePlaceholders=true`，写成
 *    `${AI_API_KEY}`（无默认）时未设置该变量不会报错，而是**原样绑成字符串 "${AI_API_KEY}"**
 *    这个非空值 —— 任何「key 配没配」的判断都拦不住它，请求会带着字面量当 key 发出去撞 401，
 *    被翻译成 6004「AI 服务调用失败」，用户以为服务坏了，其实压根没配。
 *    带空默认值的 `${AI_API_KEY:}` 才得到空串，由 PlanAiClient#credentialsMissing 明确翻译成
 *    6001「AI 服务未启用」。
 */
class ProdConfigGuardrailTest {

    /** 真实 application-prod.yml 装进 Environment —— 走 Environment 才能解析 ${VAR:...} 占位符 */
    private static MockEnvironment prodEnv() throws IOException {
        MockEnvironment env = new MockEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));
        for (PropertySource<?> ps : sources) {
            env.getPropertySources().addFirst(ps);
        }
        return env;
    }

    @Test
    void redisTimeoutMustBeSubSecondScaleInProd() throws IOException {
        Duration timeout = Binder.get(prodEnv())
                .bind("spring.data.redis.timeout", Bindable.of(Duration.class))
                .orElse(null);

        assertNotNull(timeout,
                "必须显式配置 spring.data.redis.timeout：不配则 Lettuce 默认 60s，"
                        + "Redis 网络悬挂时每个请求卡满 60s 才 fail-open，前端 15s 就已超时");
        assertTrue(timeout.getSeconds() <= 5,
                "Redis 命令超时必须压到秒级（当前 " + timeout + "）：它只是黑名单/限流的旁路依赖，"
                        + "不值得让每个请求等它");
    }

    @Test
    void unsetApiKeyBindsToEmptyStringNotToTheLiteralPlaceholder() throws IOException {
        // MockEnvironment 里没有 AI_API_KEY，模拟未配置的生产环境
        String apiKey = prodEnv().getProperty("spring.ai.anthropic.api-key");

        assertNotNull(apiKey, "prod 必须显式声明 spring.ai.anthropic.api-key");
        assertEquals("", apiKey,
                "未设置 AI_API_KEY 时必须解析成空串。若这里拿到 \"${AI_API_KEY}\" 字面量，"
                        + "说明占位符漏了空默认值 `:`，它会被当成已配置的 key 发出去撞 401 → 误报 6004");
    }

    /** 反向护栏：真的配了 key 时不能被上面的逻辑误判为空 */
    @Test
    void configuredApiKeyIsVisible() throws IOException {
        MockEnvironment env = prodEnv();
        env.setProperty("AI_API_KEY", "sk-live-test");

        assertEquals("sk-live-test", env.getProperty("spring.ai.anthropic.api-key"));
    }
}
