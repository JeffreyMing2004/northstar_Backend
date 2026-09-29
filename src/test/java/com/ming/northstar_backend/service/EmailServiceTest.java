package com.ming.northstar_backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private static final int CODE_TTL_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 5;

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, redis, CODE_TTL_SECONDS, MAX_ATTEMPTS);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void verificationCodesAreScopedToTheirPurpose() {
        when(values.get("northstar:email:code:reset:user@example.com")).thenReturn("123456");

        assertTrue(service.verifyCode(" User@Example.com ", "123456", "reset"));
        assertFalse(service.verifyCode("user@example.com", "123456", "register"));

        verify(redis).delete("northstar:email:code:reset:user@example.com");
    }

    @Test
    void sendStoresResetCodeUnderResetPurposeWithConfiguredTtl() {
        when(redis.hasKey("northstar:email:code:cooldown:user@example.com")).thenReturn(false);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        service.sendVerificationCode("User@Example.com", "reset");

        verify(values).set(
            eq("northstar:email:code:reset:user@example.com"),
            matches("\\d{6}"),
            eq(Duration.ofSeconds(CODE_TTL_SECONDS))
        );
    }

    @Test
    void sendRejectsUnknownPurpose() {
        assertThrows(RuntimeException.class,
            () -> service.sendVerificationCode("user@example.com", "login"));
    }

    /**
     * NS-03：6 位纯数字验证码必须限制错误次数，超过上限即作废该次发码，
     * 否则 5 分钟 TTL 内足以被脚本穷举。
     */
    @Test
    void wrongCodeIsInvalidatedAfterTooManyAttempts() {
        when(values.get("northstar:email:code:reset:user@example.com")).thenReturn("111111");
        when(values.increment("northstar:email:code:attempts:reset:user@example.com"))
                .thenReturn(1L, 2L, 3L, 4L, (long) MAX_ATTEMPTS);

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertFalse(service.verifyCode("user@example.com", "000000", "reset"));
        }
        // 验证码本身仍在，只是错误计数已到临界
        verify(redis, never()).delete("northstar:email:code:attempts:reset:user@example.com");

        assertFalse(service.verifyCode("user@example.com", "000000", "reset"));

        // 达到上限：验证码与错误计数一起清掉，玩家必须重新获取
        verify(redis).delete("northstar:email:code:reset:user@example.com");
        verify(redis).delete("northstar:email:code:attempts:reset:user@example.com");
    }

    @Test
    void successfulVerificationClearsAttemptCounter() {
        when(values.get("northstar:email:code:register:user@example.com")).thenReturn("123456");

        assertTrue(service.verifyCode("user@example.com", "123456", "register"));

        verify(redis).delete("northstar:email:code:register:user@example.com");
        verify(redis).delete("northstar:email:code:attempts:register:user@example.com");
    }
}
