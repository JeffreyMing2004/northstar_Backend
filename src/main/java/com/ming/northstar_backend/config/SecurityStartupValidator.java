package com.ming.northstar_backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动期安全自检：宁可起不来，也不要带着开发默认值上线。
 *
 * <p>渗透报告的核心结论是「生产沿用了开发默认配置」，所以这里的策略是
 * <b>fail fast</b>——配置不合格直接抛异常终止启动，而不是打条日志继续跑。</p>
 *
 * <p>检查项：</p>
 * <ol>
 *   <li>{@code jwt.secret}：必须存在、长度 ≥ 32 字节（HS256 强密钥要求）、
 *       且不在已知弱值/占位值名单里。</li>
 *   <li>{@code northstar.bridge.secret}：必须存在、长度 ≥ 16、
 *       且不再是 {@code local-bridge-secret} 这类默认值。</li>
 *   <li><b>远程数据层隔离</b>（对应 NS-01 的根因
 *       {@code spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}}）：
 *       数据库与 Redis 的地址只要不在本机，就必须运行在 {@code prod} profile 下。
 *       开发 profile（显式 {@code local}，或任何 profile 都没设）一旦指向远程地址
 *       <b>直接拒绝启动</b>——远程数据层只允许上线环境连接。
 *       这一条同时挡住了「生产忘记设 SPRING_PROFILES_ACTIVE 于是静默连上真实数据」，
 *       因为那种情况下 profile 会落回开发值，而地址是远程的。</li>
 *   <li>生产 profile 下 {@code ddl-auto} 仍是 {@code update/create/create-drop} 时给出告警
 *       （NS-12：生产不应让 Hibernate 自动改表）。</li>
 * </ol>
 */
@Component
public class SecurityStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupValidator.class);

    /**
     * 通用弱口令/占位值。这些是「谁都能猜到的名字」，不是任何一处真实凭据，
     * 可以直接明文列出，便于排查时一眼看懂。
     */
    private static final Set<String> FORBIDDEN_JWT_SECRETS = Set.of(
            "secret", "changeme", "change-me", "test", "jwt-secret", "northstar",
            "northstar-secret-key", "jwt-secret-key"
    );
    private static final Set<String> FORBIDDEN_BRIDGE_SECRETS = Set.of(
            "local-bridge-secret", "secret", "changeme", "change-me", "test", "bridge-secret"
    );

    /**
     * 渗透报告确认已泄露的那串真实 JWT 密钥，<b>只保存 SHA-256</b>（小写十六进制）。
     *
     * <p>为什么不写明文：本仓库是公开的，把一串真实（很可能仍在生效的）签名密钥
     * 再写进源码，等于把「伪造任意用户管理员令牌」的钥匙挂在墙上——
     * 那正是 NS-01 被利用的方式， remediation 自己不该再制造一次。
     * 摘要同样能拦住「从备份里把旧密钥还原回来」这种事故。</p>
     */
    private static final Set<String> FORBIDDEN_JWT_SECRET_SHA256 = Set.of(
            "f88fe7a3fbeebc5f4b0015ab451f99b2c0d2dc2ba576930214d59ec4105e8244"
    );

    /** 从 JDBC URL 取出主机名，兼顾 {@code jdbc:mysql://}、{@code jdbc:h2:tcp://} 等写法。 */
    private static final Pattern HOST_IN_JDBC = Pattern.compile("jdbc:[a-zA-Z0-9]+://([^/:?]+)");
    /** IPv4 回环段：127.0.0.0/8 整段都指向本机。 */
    private static final Pattern LOOPBACK_IPV4 = Pattern.compile("127(?:\\.\\d{1,3}){3}");
    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final int MIN_BRIDGE_SECRET_LENGTH = 16;

    private final Environment environment;
    private final String jwtSecret;
    private final String bridgeSecret;
    private final String datasourceUrl;
    private final String ddlAuto;
    private final String bridgeAllowedIps;
    private final String redisPassword;
    private final String redisHost;

    public SecurityStartupValidator(Environment environment,
                                    @Value("${jwt.secret:}") String jwtSecret,
                                    @Value("${northstar.bridge.secret:}") String bridgeSecret,
                                    @Value("${spring.datasource.url:}") String datasourceUrl,
                                    @Value("${spring.jpa.hibernate.ddl-auto:}") String ddlAuto,
                                    @Value("${northstar.bridge.allowed-ips:}") String bridgeAllowedIps,
                                    @Value("${spring.data.redis.password:}") String redisPassword,
                                    @Value("${spring.data.redis.host:}") String redisHost) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.bridgeSecret = bridgeSecret;
        this.datasourceUrl = datasourceUrl;
        this.ddlAuto = ddlAuto;
        this.bridgeAllowedIps = bridgeAllowedIps;
        this.redisPassword = redisPassword;
        this.redisHost = redisHost;
    }

    @PostConstruct
    public void verify() {
        checkJwtSecret();
        checkBridgeSecret();
        checkDataLayerIsolation();
        warnAboutSoftSpots();
    }

    private void checkJwtSecret() {
        String value = jwtSecret == null ? "" : jwtSecret.trim();
        if (value.isEmpty() || isUnresolvedPlaceholder(value)) {
            throw new IllegalStateException(banner(
                    "未配置 JWT_SECRET",
                    "JWT 签名密钥必须由环境变量 JWT_SECRET 注入（64 位随机字符串），"
                            + "禁止写进仓库里的任何配置文件。\n"
                            + "     生成方式：openssl rand -base64 48"));
        }
        // 先判「已知弱值 / 已泄露值」：这样报错能直接告诉运维「这串已经被公开过，不能再用了」，
        // 而不是笼统的「长度不足」；同时也避免这些短值被长度检查提前拦下、
        // 让这份名单变成永远走不到的死代码。
        String normalized = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_JWT_SECRETS.contains(normalized)
                || matchesAnySha256(normalized, FORBIDDEN_JWT_SECRET_SHA256)) {
            throw new IllegalStateException(banner(
                    "JWT_SECRET 仍是已知的弱值（或渗透报告里泄露过的那串）",
                    "该值已随仓库/报告泄露，任何人都能据此伪造管理员令牌。"
                            + "请立即轮换：openssl rand -base64 48"));
        }
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(banner(
                    "JWT_SECRET 强度不足",
                    "当前长度 " + bytes + " 字节，HS256 要求至少 " + MIN_JWT_SECRET_BYTES
                            + " 字节。生成方式：openssl rand -base64 48"));
        }
    }

    private void checkBridgeSecret() {
        String value = bridgeSecret == null ? "" : bridgeSecret.trim();
        if (value.isEmpty() || isUnresolvedPlaceholder(value)) {
            throw new IllegalStateException(banner(
                    "未配置 NORTHSTAR_BRIDGE_SECRET",
                    "MC 插件与后端的共享令牌必须显式配置，不再提供 local-bridge-secret 之类的默认值。"));
        }
        if (value.length() < MIN_BRIDGE_SECRET_LENGTH
                || FORBIDDEN_BRIDGE_SECRETS.contains(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(banner(
                    "NORTHSTAR_BRIDGE_SECRET 强度不足或仍为默认值",
                    "请改用 32 字节随机令牌，并同步更新 MC 插件配置；"
                            + "该令牌决定谁能上报服务器状态与房间数据。"));
        }
    }

    /**
     * 远程数据层隔离：<b>远程数据库与远程 Redis 只允许上线环境（prod profile）连接。</b>
     *
     * <p>开发 profile（显式 {@code local}，或任何 profile 都没设）只要把地址指向非本机，
     * 就直接拒绝启动。这里刻意<b>不提供任何放行开关</b>：只要存在「显式声明即可连远程库」
     * 的开关，就意味着生产忘记设 {@code SPRING_PROFILES_ACTIVE=prod} 时那套配置依然成立、
     * 依然会静默连上真实数据——那正是 NS-01 的事故形状。</p>
     *
     * <p>本机开发请使用本机 MySQL / Redis（默认 {@code 127.0.0.1}）。</p>
     */
    private void checkDataLayerIsolation() {
        List<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .toList();
        boolean developmentProfile = profiles.isEmpty() || profiles.contains("local");

        if (profiles.contains("local")) {
            log.warn("[NorthStar] 当前运行在 local（开发）profile。"
                    + "生产环境必须显式设置 SPRING_PROFILES_ACTIVE=prod。");
        }

        if (!developmentProfile) {
            String ddl = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(Locale.ROOT);
            if ("update".equals(ddl) || "create".equals(ddl) || "create-drop".equals(ddl)) {
                log.warn("[NorthStar] 生产 profile 下 ddl-auto=" + ddl
                        + "：Hibernate 仍会自动改表（NS-12），建议改为 validate。");
            }
            return;
        }

        String databaseHost = resolveDatabaseHost();
        if (databaseHost != null && !isLocalHost(databaseHost)) {
            throw new IllegalStateException(banner(
                    "开发环境不允许连接远程数据库",
                    "当前 profile=" + profileLabel(profiles) + "，而数据库地址为 " + databaseHost + "。\n"
                            + "     远程数据库只允许在上线环境（prod profile）连接。\n"
                            + "     本机开发请改用本机 MySQL（如 127.0.0.1:3306）；\n"
                            + "     若这确实是上线机器，请显式设置 SPRING_PROFILES_ACTIVE=prod。"));
        }

        String redis = redisHost == null ? "" : redisHost.trim();
        if (!redis.isEmpty() && !isLocalHost(redis)) {
            throw new IllegalStateException(banner(
                    "开发环境不允许连接远程 Redis",
                    "当前 profile=" + profileLabel(profiles) + "，而 Redis 地址为 " + redis + "。\n"
                            + "     远程数据层只允许在上线环境（prod profile）连接；\n"
                            + "     本机开发请改用本机 Redis（如 127.0.0.1:6379）。"));
        }
    }

    private String profileLabel(List<String> profiles) {
        return profiles.isEmpty() ? "(未显式设置)" : String.join(",", profiles);
    }

    private void warnAboutSoftSpots() {
        if (bridgeAllowedIps == null || bridgeAllowedIps.isBlank()) {
            log.warn("[NorthStar] 未配置 northstar.bridge.allowed-ips：/api/bridge/** 仅靠共享令牌鉴权。"
                    + "建议收敛为 MC 服务器的出口 IP（支持单个 IP 与 CIDR，逗号分隔）。");
        }
        String url = datasourceUrl == null ? "" : datasourceUrl.toLowerCase(Locale.ROOT);
        if (url.contains("usessl=false") || url.contains("sslmode=disabled")) {
            log.warn("[NorthStar] 数据库连接未启用 TLS（url 含 useSSL=false）："
                    + "跨公网访问时凭据与数据均为明文，建议内网直连或启用 sslMode=REQUIRED。");
        }
        if (isBlank(redisPassword) && !isLocalProfile()) {
            // Redis 要求认证却拿不到口令时，缓存与限流会被各处 try/catch 静默吞掉：
            // 站点看起来正常，实际限流已失效，属于「静默降级」，必须显式告警。
            log.warn("[NorthStar] 未配置 REDIS_PASSWORD（且非本机 profile）："
                    + "若 Redis 要求认证，缓存与全局限流会静默失效，请核对部署环境变量。");
        }
    }

    private boolean isLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> "local".equalsIgnoreCase(p));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveDatabaseHost() {
        if (datasourceUrl == null) {
            return null;
        }
        Matcher matcher = HOST_IN_JDBC.matcher(datasourceUrl.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 主机名是否指向本机。只有本机地址才允许在开发 profile 下使用——
     * 远程数据层一律要求 prod profile（见 {@link #checkDataLayerIsolation()}）。
     */
    private boolean isLocalHost(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return value.equals("localhost")
                || value.equals("::1")
                || value.equals("0:0:0:0:0:0:0:1")
                || LOOPBACK_IPV4.matcher(value).matches()
                || value.endsWith(".local");
    }

    /**
     * Spring 的宽松绑定会把解析不到的占位符原样留下（例如 {@code "${JWT_SECRET}"}），
     * 这里要把它识别成「没配置」，而不是当成一个 14 字节的弱密钥。
     */
    private boolean isUnresolvedPlaceholder(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    /** 小写十六进制 SHA-256，用于比对那些「不便明文列出」的已泄露密钥。 */
    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须实现的算法，正常环境不会走到这里
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 摘要比对单独抽成方法，好让单元测试用「自造的明文 → 摘要」覆盖这条链路，
     * 而测试文件里不必再出现那串真实密钥。
     */
    static boolean matchesAnySha256(String value, Set<String> expectedSha256) {
        return expectedSha256.contains(sha256Hex(value));
    }

    private String banner(String title, String detail) {
        return "\n\n==================================================================\n"
                + " NorthStar 安全自检未通过：" + title + "\n"
                + "------------------------------------------------------------------\n"
                + "     " + detail + "\n"
                + " 参见 docs/SECURITY-REMEDIATION-20260929.md\n"
                + "==================================================================\n";
    }
}
