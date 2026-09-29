package com.ming.northstar_backend.service;

import com.ming.northstar_backend.config.AuthRateLimitProperties;
import com.ming.northstar_backend.support.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NS-03 / NS-08：登录、发码、找回、重置四条链路都得有闸门，
 * 且失败计数按「账号」维度累计、成功即清零。
 */
class AuthGuardTest {

    private final RateLimitService limiter = mock(RateLimitService.class);
    private final AuthRateLimitProperties policy = new AuthRateLimitProperties(
            30, 300, 10, 900, false, 1000, 5, 600, 10, 600, 10, 600);

    private AuthGuard guard;

    @BeforeEach
    void setUp() {
        when(limiter.allow(anyString(), any(), anyInt(), any())).thenReturn(true);
        guard = new AuthGuard(limiter, policy);
    }

    @Test
    void loginIsRejectedOncePerIpLimitIsHit() {
        when(limiter.allow(eq("login-ip"), eq("1.2.3.4"), eq(30), eq(Duration.ofSeconds(300))))
                .thenReturn(false);

        assertThrows(RateLimitExceededException.class, () -> guard.assertLoginAllowed("1.2.3.4", "JeffreyMing"));
    }

    @Test
    void accountIsLockedAfterTooManyFailuresRegardlessOfIp() {
        when(limiter.hits("login-account-fail", "jeffreyming")).thenReturn(10L);
        when(limiter.ttlSeconds("login-account-fail", "jeffreyming")).thenReturn(420L);

        RateLimitExceededException error = assertThrows(RateLimitExceededException.class,
                () -> guard.assertLoginAllowed("9.9.9.9", "JeffreyMing"));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("420"));
    }

    @Test
    void failuresAreCountedPerAccountAndClearedOnSuccess() {
        guard.recordLoginFailure("1.2.3.4", " JeffreyMing ");
        verify(limiter).increment("login-account-fail", "jeffreyming", Duration.ofSeconds(900));

        guard.clearLoginFailures("JeffreyMing");
        verify(limiter).clear("login-account-fail", "jeffreyming");
    }

    @Test
    void sendCodeIsLimitedPerIp() {
        when(limiter.allow(eq("send-code-ip"), eq("1.2.3.4"), eq(5), eq(Duration.ofSeconds(600))))
                .thenReturn(false);

        assertThrows(RateLimitExceededException.class, () -> guard.assertSendCodeAllowed("1.2.3.4"));
    }

    @Test
    void forgotLookupAndResetAreLimitedPerIp() {
        assertDoesNotThrow(() -> guard.assertForgotLookupAllowed("1.2.3.4"));
        assertDoesNotThrow(() -> guard.assertResetAllowed("1.2.3.4"));

        when(limiter.allow(eq("forgot-lookup-ip"), eq("1.2.3.4"), anyInt(), any())).thenReturn(false);
        when(limiter.allow(eq("reset-ip"), eq("1.2.3.4"), anyInt(), any())).thenReturn(false);

        assertThrows(RateLimitExceededException.class, () -> guard.assertForgotLookupAllowed("1.2.3.4"));
        assertThrows(RateLimitExceededException.class, () -> guard.assertResetAllowed("1.2.3.4"));
    }
}
