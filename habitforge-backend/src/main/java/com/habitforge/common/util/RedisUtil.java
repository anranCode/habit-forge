package com.habitforge.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis 工具（用途：token 黑名单 + 接口限流）
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private static final String BLACKLIST_PREFIX = "habitforge:token:blacklist:";
    private static final String RATE_LIMIT_PREFIX = "habitforge:rate:";

    /** fenced unlock: 仅当锁 value 与 owner 一致(即仍由本持有者持有)才删除, 否则原样返回 0 */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

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

    // ============ 一次性事件标记：SETNX + TTL ============

    /**
     * 原子"仅首次"标记（如当日奖励发放标记）。
     *
     * @return true = 本次设置成功(此前不存在); false = 标记已存在
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, value, ttl));
    }

    // ============ 分布式锁 + 限流退还（AI 生成防双击/费用退还） ============

    /**
     * 抢锁：SETNX + 秒级 TTL（key 由调用方给全名，如 habitforge:ai:lock:{userId}，沿 setIfAbsent 约定不加前缀）。
     * value 必须是调用方唯一持有的 owner 标识，解锁时校验，避免锁过期后误删他人锁。
     *
     * @return true = 抢到; false = 已有人持有
     */
    public boolean tryLock(String key, String value, long timeoutSeconds) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(timeoutSeconds)));
    }

    /**
     * 释放锁：Lua 原子比对 value == owner 才 DEL（fenced unlock）。
     * value 恒为他人持有（本锁已过期又被他人抢到）时不做任何删除。
     *
     * @return true = 确为本持有者并删除; false = 锁已不属于该 owner（过期/被他人持有）
     */
    public boolean unlock(String key, String owner) {
        if (owner == null) {
            return false;
        }
        Long deleted = redis.execute(
                new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                Collections.singletonList(key), owner);
        return deleted != null && deleted > 0;
    }

    /**
     * 限流退还：对 tryAcquire 计数 DECR 1（上游失败未耗费用时调用）。
     * 计数被减为负（窗口已翻转后误退）则直接删键。
     */
    public void release(String key) {
        String fullKey = RATE_LIMIT_PREFIX + key;
        Long count = redis.opsForValue().increment(fullKey, -1);
        if (count != null && count < 0) {
            redis.delete(fullKey);
        }
    }
}
