package com.habitforge.service;

import com.habitforge.common.constant.AppConstant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B1 超时护栏(配置级校验)：AnthropicApi 的非流式调用走 Boot 自动配置的 RestClient.Builder,
 * 超时经 spring.http.client 生效; 目标不变式 最坏耗时 = read-timeout × 重试次数 < AI_LOCK_TTL_SECONDS。
 */
class AiHttpTimeoutConfigTest {

    private static String prop(String key) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        for (PropertySource<?> ps : sources) {
            Object v = ps.getProperty(key);
            if (v != null) {
                return v.toString();
            }
        }
        return null;
    }

    private static long seconds(String value) {
        if (value.endsWith("ms")) {
            return Long.parseLong(value.substring(0, value.length() - 2)) / 1000;
        }
        if (value.endsWith("s")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        }
        return Duration.parse(value).toSeconds();
    }

    @Test
    void httpClientTimeoutsConfiguredAndWorstCaseWithinLockTtl() throws IOException {
        String connect = prop("spring.http.client.connect-timeout");
        String read = prop("spring.http.client.read-timeout");
        assertNotNull(connect, "spring.http.client.connect-timeout 必须配置");
        assertNotNull(read, "spring.http.client.read-timeout 必须配置");

        assertEquals(5L, seconds(connect), "connect-timeout 应为 5s");
        assertEquals(45L, seconds(read), "read-timeout 应为 45s");

        String attempts = prop("spring.ai.retry.max-attempts");
        assertNotNull(attempts, "spring.ai.retry.max-attempts 必须配置(决定最坏耗时)");
        assertTrue(seconds(read) * Long.parseLong(attempts) < AppConstant.AI_LOCK_TTL_SECONDS,
                "read-timeout×max-attempts 必须小于锁 TTL, 否则锁先过期会退化成非 fenced 锁");
    }
}
