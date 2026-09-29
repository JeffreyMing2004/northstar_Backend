package com.ming.northstar_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NS-09：限流计数必须落在 Redis（跨实例一致、重启不清零），
 * 且 Redis 故障时选择「放行」而不是把限流变成登录不可用的单点故障。
 */
class RateLimitServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new RateLimitService(redis);
    }

    @Test
    void allowsUntilLimitIsReachedThenRejects() {
        when(values.increment("northstar:rl:login-ip:1.2.3.4")).thenReturn(1L, 2L, 3L);

        assertTrue(service.allow("login-ip", "1.2.3.4", 2, Duration.ofMinutes(1)));
        assertTrue(service.allow("login-ip", "1.2.3.4", 2, Duration.ofMinutes(1)));
        assertFalse(service.allow("login-ip", "1.2.3.4", 2, Duration.ofMinutes(1)));
    }

    @Test
    void setsWindowOnFirstHit() {
        when(values.increment(any())).thenReturn(1L);

        service.allow("send-code-ip", "1.2.3.4", 5, Duration.ofSeconds(600));

        verify(redis).expire(eq("northstar:rl:send-code-ip:1.2.3.4"), eq(Duration.ofSeconds(600)));
    }

    @Test
    void limitZeroMeansUnlimitedAndSkipsRedis() {
        assertTrue(service.allow("login-ip", "1.2.3.4", 0, Duration.ofMinutes(1)));
        verify(redis, never()).opsForValue();
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(values.increment(any())).thenThrow(new RuntimeException("connection refused"));

        assertTrue(service.allow("login-ip", "1.2.3.4", 1, Duration.ofMinutes(1)));
    }

    @Test
    void hitCountIsNormalizedToLowerCase() {
        when(values.get("northstar:rl:login-account-fail:JeffreyMing".toLowerCase(java.util.Locale.ROOT)))
                .thenReturn("7");

        assertTrue(service.hits("login-account-fail", "JeffreyMing") == 7L);
    }
}
