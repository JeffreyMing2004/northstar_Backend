package com.ming.northstar_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NS-01：启动期必须 fail fast——带着开发默认值或弱密钥的服务不应该被拉起来。
 */
class SecurityStartupValidatorTest {

    private static final String STRONG_JWT = "c5f0b0a45a6a4e2f9c0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d";
    private static final String STRONG_BRIDGE = "b7d1c2a3f4e5d6c7b8a9f0e1d2c3b4a5";
    private static final String REMOTE_DB = "jdbc:mysql://43.136.177.54:3306/web?useSSL=false";

    private SecurityStartupValidator validator(String jwt, String bridge, String url, String ddl,
                                              boolean strict, boolean allowRemote, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new SecurityStartupValidator(environment, jwt, bridge, url, ddl, strict, allowRemote,
                "127.0.0.1", "test-redis-password");
    }

    @Test
    void acceptsStrongSecretsUnderProdProfile() {
        assertDoesNotThrow(() -> validator(STRONG_JWT, STRONG_BRIDGE, REMOTE_DB, "validate", true, false, "prod")
                .verify());
    }

    @Test
    void rejectsMissingSecret() {
        assertThrows(IllegalStateException.class,
                () -> validator("", STRONG_BRIDGE, REMOTE_DB, "validate", true, false, "prod").verify());
    }

    /** 宽松绑定会把解析不到的占位符原样留下，这必须被识别成「没配置」。 */
    @Test
    void rejectsUnresolvedPlaceholder() {
        assertThrows(IllegalStateException.class,
                () -> validator("${JWT_SECRET}", STRONG_BRIDGE, REMOTE_DB, "validate", true, false, "prod").verify());
    }

    /**
     * 报告里那串真实密钥只以 SHA-256 参与比对——仓库是公开的，把仍在生效的签名密钥
     * 再写进源码，等于把伪造管理员令牌的钥匙挂在墙上（NS-01 的翻版）。
     * 因此这里用「标准向量确认摘要实现正确 + 自造的明文/摘要对确认比对链路」来覆盖，
     * 测试文件里不出现任何真实密钥。
     */
    @Test
    void matchesForbiddenSecretBySha256Digest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                SecurityStartupValidator.sha256Hex("abc"), "SHA-256 实现必须与标准向量一致");

        assertTrue(SecurityStartupValidator.matchesAnySha256(
                        "any-candidate-value", Set.of(SecurityStartupValidator.sha256Hex("any-candidate-value"))),
                "命中摘要名单时应判定为禁用");
        assertFalse(SecurityStartupValidator.matchesAnySha256(
                        "any-candidate-value", Set.of(SecurityStartupValidator.sha256Hex("some-other-value"))),
                "未命中摘要名单时不应误杀");
    }

    /** 已知弱值/占位值要给出「这串不能再用」的专门报错，而不是笼统的长度不足。 */
    @Test
    void rejectsWellKnownWeakSecretWithDedicatedMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> validator("secret", STRONG_BRIDGE, REMOTE_DB, "validate", true, false, "prod").verify());
        assertTrue(error.getMessage().contains("泄露"), "应命中已知弱值分支：" + error.getMessage());
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalStateException.class,
                () -> validator("short-secret", STRONG_BRIDGE, REMOTE_DB, "validate", true, false, "prod").verify());
    }

    @Test
    void rejectsDefaultBridgeSecret() {
        assertThrows(IllegalStateException.class,
                () -> validator(STRONG_JWT, "local-bridge-secret", REMOTE_DB, "validate", true, false, "prod").verify());
        assertThrows(IllegalStateException.class,
                () -> validator(STRONG_JWT, "too-short", REMOTE_DB, "validate", true, false, "prod").verify());
    }

    /**
     * 报告的核心事故形状：生产忘记设 SPRING_PROFILES_ACTIVE，于是落到 local，
     * 连上远程库并且继续让 Hibernate 自动改表。这种组合必须直接拒绝启动。
     */
    @Test
    void rejectsDevProfileWithRemoteDatabaseAndAutoDdl() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> validator(STRONG_JWT, STRONG_BRIDGE, REMOTE_DB, "update", true, false, "local").verify());
        assertTrue(error.getMessage().contains("SPRING_PROFILES_ACTIVE=prod"));

        // 未显式声明任何 profile 时同样按开发 profile 处理
        assertThrows(IllegalStateException.class,
                () -> validator(STRONG_JWT, STRONG_BRIDGE, REMOTE_DB, "update", true, false).verify());
    }

    @Test
    void allowsLocalDevelopmentAgainstRemoteDatabaseWhenExplicitlyAcknowledged() {
        assertDoesNotThrow(() -> validator(STRONG_JWT, STRONG_BRIDGE, REMOTE_DB, "update", true, true, "local")
                .verify());
    }

    @Test
    void allowsLocalProfileAgainstLocalDatabase() {
        assertDoesNotThrow(() -> validator(STRONG_JWT, STRONG_BRIDGE,
                "jdbc:mysql://127.0.0.1:3306/web", "update", true, false, "local").verify());
    }

    @Test
    void guardsCanBeDisabledForSpecialEnvironments() {
        assertDoesNotThrow(() -> validator(STRONG_JWT, STRONG_BRIDGE, REMOTE_DB, "update", false, false, "local")
                .verify());
    }
}
