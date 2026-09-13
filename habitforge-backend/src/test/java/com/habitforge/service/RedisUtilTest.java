package com.habitforge.service;

import com.habitforge.common.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分布式锁 fenced unlock：只有持锁者(owner value 匹配)才能删锁, 锁过期被他人抢到后不得误删
 */
@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    private static final String LOCK_KEY = "habitforge:ai:lock:user-a";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RedisUtil redisUtil;

    /** 非持有者 unlock：Lua 比对 value 不符返回 0, 绝不删除锁 */
    @Test
    @SuppressWarnings("unchecked")
    void unlock_nonOwner_doesNotDeleteLock() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(0L);

        assertFalse(redisUtil.unlock(LOCK_KEY, "other-owner"));

        verify(redis, never()).delete(anyString()); // 不得无条件 delete
    }

    /** 持有者 unlock：走 compare-and-delete Lua, 命中才删 */
    @Test
    @SuppressWarnings("unchecked")
    void unlock_owner_deletesViaCompareAndDeleteScript() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(1L);

        assertTrue(redisUtil.unlock(LOCK_KEY, "owner-1"));

        ArgumentCaptor<DefaultRedisScript<Long>> captor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(captor.capture(), anyList(), eq("owner-1"));
        String script = captor.getValue().getScriptAsString();
        assertTrue(script.contains("'get'"), script); // 先比对 value
        assertTrue(script.contains("'del'"), script); // 再删除
        verify(redis, never()).delete(anyString());
    }

    /** owner 为 null 时直接判失败, 不触达 Redis */
    @Test
    @SuppressWarnings("unchecked")
    void unlock_nullOwner_neverTouchesRedis() {
        assertFalse(redisUtil.unlock(LOCK_KEY, null));

        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    // ============ currentCount：读额度已用数（供展示, 必须与 tryAcquire 同源） ============

    /** 读的必须是 tryAcquire 写的同一个键（含 RATE_LIMIT_PREFIX）, 否则展示与裁决分叉 */
    @Test
    void currentCount_readsPrefixedKeyUsedByTryAcquire() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("habitforge:rate:ai:plan:user-a:2026-09-13")).thenReturn("3");

        assertEquals(3, redisUtil.currentCount("ai:plan:user-a:2026-09-13"));
    }

    /** 键不存在 = 今日一次未用（不是错误） */
    @Test
    void currentCount_absentKeyIsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        assertEquals(0, redisUtil.currentCount("ai:plan:user-a:2026-09-13"));
    }

    /** 值被写坏时不抛异常, 按 0 处理（展示层不该因脏数据 500） */
    @Test
    void currentCount_nonNumericValueIsZero() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("not-a-number");

        assertEquals(0, redisUtil.currentCount("ai:plan:user-a:2026-09-13"));
    }

    /** Redis 故障要向上抛, 由调用方决定降级策略（不能在这里悄悄吞成 0） */
    @Test
    void currentCount_redisFailurePropagates() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThrows(RuntimeException.class, () -> redisUtil.currentCount("ai:plan:user-a:2026-09-13"));
    }
}
