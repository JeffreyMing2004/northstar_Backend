package com.ming.northstar_backend.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** NS-10：注册与重置必须共用同一套密码强度策略。 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(8);

    @Test
    void acceptsReasonablePassword() {
        assertDoesNotThrow(() -> policy.validate("NorthStar2026", "密码"));
    }

    @Test
    void rejectsTooShortPassword() {
        assertEquals("密码长度不能少于8位",
                assertThrows(RuntimeException.class, () -> policy.validate("Ab12", "密码")).getMessage());
    }

    @Test
    void rejectsSingleClassPassword() {
        assertEquals("密码需同时包含字母和数字",
                assertThrows(RuntimeException.class, () -> policy.validate("abcdefgh", "密码")).getMessage());
        assertEquals("密码需同时包含字母和数字",
                assertThrows(RuntimeException.class, () -> policy.validate("12345678", "密码")).getMessage());
    }

    @Test
    void rejectsWhitespaceAndBlank() {
        assertEquals("密码不能包含空格",
                assertThrows(RuntimeException.class, () -> policy.validate("abcdef 123", "密码")).getMessage());
        assertEquals("请输入密码",
                assertThrows(RuntimeException.class, () -> policy.validate("", "密码")).getMessage());
    }

    /** 超过 BCrypt 有效长度会被截断，因此直接拒绝而不是静默丢弃。 */
    @Test
    void rejectsOverlongPassword() {
        String longPassword = "a1".repeat(40);
        assertEquals("密码长度不能超过72位",
                assertThrows(RuntimeException.class, () -> policy.validate(longPassword, "密码")).getMessage());
    }

    /** 配置被误改成极小值时，也要守住 6 位这个历史下限。 */
    @Test
    void configuredMinimumNeverDropsBelowHardFloor() {
        assertEquals(6, new PasswordPolicy(2).getMinLength());
    }
}
