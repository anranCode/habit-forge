package com.habitforge.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 工具（用途：token 黑名单 + 接口限流）
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private static final String BLACKLIST_PREFIX = "habitforge:token:blacklist:";
    private static final String RATE_LIMIT_PREFIX = "habitforge:rate:";

    private final StringRedisTemplate redis;

    // ============ token 黑名单（登出后使其失效） ============

    public void addToBlacklist(String token, long ttlMillis) {
        if (ttlMillis > 0) {
            redis.opsForValue().set(BLACKLIST_PREFIX + token, "1", Duration.ofMillis(ttlMillis));
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + token));
    }

    // ============ 简单限流：固定窗口 INCR + EXPIRE ============

    /**
     * @return true = 允许通过; false = 超限
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        String fullKey = RATE_LIMIT_PREFIX + key;
        Long count = redis.opsForValue().increment(fullKey);
        if (count != null && count == 1L) {
            redis.expire(fullKey, window);
        }
        return count != null && count <= limit;
    }
}
