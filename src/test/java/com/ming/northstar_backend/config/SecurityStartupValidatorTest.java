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
 *
 * <p>同时覆盖「远程数据库 / 远程 Redis 只允许上线环境（prod profile）连接」这条硬规则。</p>
 */
class SecurityStartupValidatorTest {

    private static final String STRONG_JWT = "c5f0b0a45a6a4e2f9c0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d";
    private static final String STRONG_BRIDGE = "b7d1c2a3f4e5d6c7b8a9f0e1d2c3b4a5";
    private static final String REMOTE_DB = "jdbc:mysql://43.136.177.54:3306/web?useSSL=false";
    private static final String LOCAL_DB = "jdbc:mysql://127.0.0.1:3306/web";
    private static final String LOCAL_REDIS = "127.0.0.1";
    private static final String REMOTE_REDIS = "43.136.177.54";

    private SecurityStartupValidator build(String jwt, String bridge, String url, String ddl,
                                           String redisHost, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new SecurityStartupValidator(environment, jwt, bridge, url, ddl,
                "127.0.0.1", "test-redis-password", redisHost);
    }

    /** 生产 profile：远程数据层应当被允许。 */
    private SecurityStartupValidator prod(String url, String ddl, String redisHost) {
        return build(STRONG_JWT, STRONG_BRIDGE, url, ddl, redisHost, "prod");
    }

    /** 显式开发 profile：只允许本机数据层。 */
    private SecurityStartupValidator dev(String url, String ddl, String redisHost) {
        return build(STRONG_JWT, STRONG_BRIDGE, url, ddl, redisHost, "local");
    }

    /** 什么 profile 都没设——按开发处理（这正是 NS-01 的事故形状）。 */
    private SecurityStartupValidator noProfile(String url, String ddl, String redisHost) {
        return build(STRONG_JWT, STRONG_BRIDGE, url, ddl, redisHost);
    }

    /** 只替换密钥的入口；数据层固定为「生产可连的远程地址」。 */
    private SecurityStartupValidator prodSecrets(String jwt, String bridge) {
        return build(jwt, bridge, REMOTE_DB, "validate", LOCAL_REDIS, "prod");
    }

    // ------------------------------------------------------------------
    // 密钥
    // ------------------------------------------------------------------

    @Test
    void acceptsStrongSecretsUnderProdProfile() {
        assertDoesNotThrow(() -> prodSecrets(STRONG_JWT, STRONG_BRIDGE).verify());
    }

    @Test
    void rejectsMissingSecret() {
        assertThrows(IllegalStateException.class, () -> prodSecrets("", STRONG_BRIDGE).verify());
    }

    /** 宽松绑定会把解析不到的占位符原样留下，这必须被识别成「没配置」。 */
    @Test
    void rejectsUnresolvedPlaceholder() {
        assertThrows(IllegalStateException.class, () -> prodSecrets("${JWT_SECRET}", STRONG_BRIDGE).verify());
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalStateException.class, () -> prodSecrets("short-secret", STRONG_BRIDGE).verify());
    }

    @Test
    void rejectsDefaultBridgeSecret() {
        assertThrows(IllegalStateException.class, () -> prodSecrets(STRONG_JWT, "local-bridge-secret").verify());
        assertThrows(IllegalStateException.class, () -> prodSecrets(STRONG_JWT, "too-short").verify());
    }

    /** 已知弱值/占位值要给出「这串不能再用」的专门报错，而不是笼统的长度不足。 */
    @Test
    void rejectsWellKnownWeakSecretWithDedicatedMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> prodSecrets("secret", STRONG_BRIDGE).verify());
        assertTrue(error.getMessage().contains("泄露"), "应命中已知弱值分支：" + error.getMessage());
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

    // ------------------------------------------------------------------
    // 远程数据层只允许上线环境连接
    // ------------------------------------------------------------------

    /** 生产 profile 下，远程数据库与远程 Redis 都应放行。 */
    @Test
    void allowsRemoteDataLayerUnderProdProfile() {
        assertDoesNotThrow(() -> prod(REMOTE_DB, "validate", REMOTE_REDIS).verify());
    }

    /**
     * 报告的核心事故形状：生产忘记设 SPRING_PROFILES_ACTIVE，于是落到开发 profile，
     * 却连着远程数据库。这条必须直接拒绝启动。
     */
    @Test
    void rejectsRemoteDatabaseOutsideProdProfile() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dev(REMOTE_DB, "validate", LOCAL_REDIS).verify());
        assertTrue(error.getMessage().contains("远程数据库"), "报错应点明远程数据库：" + error.getMessage());

        // 未显式声明任何 profile 时同样按开发处理
        assertThrows(IllegalStateException.class, () -> noProfile(REMOTE_DB, "validate", LOCAL_REDIS).verify());
    }

    /** 这条不再依赖 ddl-auto：即使不开自动改表，开发 profile 也不许连远程库。 */
    @Test
    void rejectsRemoteDatabaseInDevProfileEvenWithoutAutoDdl() {
        assertThrows(IllegalStateException.class, () -> dev(REMOTE_DB, "none", LOCAL_REDIS).verify());
    }

    /** Redis 同规则。 */
    @Test
    void rejectsRemoteRedisOutsideProdProfile() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> dev(LOCAL_DB, "validate", REMOTE_REDIS).verify());
        assertTrue(error.getMessage().contains("远程 Redis"), "报错应点明远程 Redis：" + error.getMessage());

        assertThrows(IllegalStateException.class, () -> noProfile(LOCAL_DB, "validate", REMOTE_REDIS).verify());
    }

    /** 本机地址在开发 profile 下正常放行（含回环段的其它写法）。 */
    @Test
    void acceptsLocalDataLayerInDevProfile() {
        assertDoesNotThrow(() -> dev(LOCAL_DB, "update", LOCAL_REDIS).verify());
        assertDoesNotThrow(() -> dev("jdbc:mysql://localhost:3306/web", "update", "localhost").verify());
        assertDoesNotThrow(() -> dev("jdbc:mysql://127.0.0.2:3306/web", "update", "127.0.0.2").verify());
        assertDoesNotThrow(() -> noProfile(LOCAL_DB, "update", LOCAL_REDIS).verify());
    }

    /** H2 / 无主机名的 JDBC URL 解析不出主机，不应被误判成远程。 */
    @Test
    void treatsHostlessJdbcUrlAsLocal() {
        assertDoesNotThrow(() -> dev("jdbc:h2:mem:testdb", "create-drop", LOCAL_REDIS).verify());
    }

    /** 生产下 ddl-auto 仍为 update 只告警、不拒绝启动（避免维护窗口内起不来）。 */
    @Test
    void prodProfileWithAutoDdlOnlyWarns() {
        assertDoesNotThrow(() -> prod(REMOTE_DB, "update", REMOTE_REDIS).verify());
    }
}
