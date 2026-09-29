package com.ming.northstar_backend.service;

import com.ming.northstar_backend.config.AuthRateLimitProperties;
import com.ming.northstar_backend.support.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 认证接口的准入闸门：限流 + 失败计数 + 渐进延迟。
 *
 * <p>对应渗透报告：</p>
 * <ul>
 *   <li><b>NS-08</b>：{@code /api/auth/login}、{@code /api/auth/send-code} 原本完全没有
 *       IP 维度限流，发信冷却只按邮箱算（换个邮箱就绕过）。现在两条链路都按 IP 计数。</li>
 *   <li><b>NS-03</b>：{@code /api/auth/forgot-password/lookup} 与
 *       {@code /api/auth/reset-password}（都是 permitAll）被纳入同一套闸门，
 *       验证码本身的失败次数限制在 {@link EmailService} 里。</li>
 * </ul>
 *
 * <p>计数放 Redis（见 {@link RateLimitService}），多实例共享；Redis 不可用时放行，
 * 保证可用性优先。</p>
 */
@Service
public class AuthGuard {

    private static final Logger log = LoggerFactory.getLogger(AuthGuard.class);

    static final String BUCKET_LOGIN_IP = "login-ip";
    static final String BUCKET_LOGIN_ACCOUNT = "login-account-fail";
    static final String BUCKET_SEND_CODE_IP = "send-code-ip";
    static final String BUCKET_FORGOT_LOOKUP_IP = "forgot-lookup-ip";
    static final String BUCKET_RESET_IP = "reset-ip";

    /** 多少次失败之后才开始加延迟：太早会误伤正常输错的玩家。 */
    private static final long DELAY_AFTER_FAILURES = 3;

    private final RateLimitService limiter;
    private final AuthRateLimitProperties policy;

    public AuthGuard(RateLimitService limiter, AuthRateLimitProperties policy) {
        this.limiter = limiter;
        this.policy = policy;
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    /**
     * 登录前置检查。账号维度只看「失败次数」，成功登录会清零，
     * 因此正常玩家不会被自己的成功登录拖黑。
     */
    public void assertLoginAllowed(String ip, String account) {
        if (!limiter.allow(BUCKET_LOGIN_IP, ip, policy.loginIpLimit(),
                Duration.ofSeconds(policy.loginIpWindowSeconds()))) {
            throw new RateLimitExceededException("登录尝试过于频繁，请稍后再试",
                    policy.loginIpWindowSeconds());
        }
        String key = accountKey(account);
        if (key != null && limiter.hits(BUCKET_LOGIN_ACCOUNT, key) >= policy.loginAccountFailLimit()) {
            long ttl = limiter.ttlSeconds(BUCKET_LOGIN_ACCOUNT, key);
            throw new RateLimitExceededException(
                    "该账号连续登录失败次数过多，请 " + Math.max(ttl, 60) + " 秒后再试",
                    Math.max(ttl, 60));
        }
    }

    /** 记录一次登录失败，并按失败次数加渐进延迟。 */
    public void recordLoginFailure(String ip, String account) {
        String key = accountKey(account);
        if (key == null) {
            return;
        }
        long failures = limiter.increment(BUCKET_LOGIN_ACCOUNT, key,
                Duration.ofSeconds(policy.loginAccountWindowSeconds()));
        if (!policy.loginDelayEnabled() || failures < DELAY_AFTER_FAILURES) {
            return;
        }
        long delay = Math.min((failures - DELAY_AFTER_FAILURES + 1) * 250L, policy.loginDelayMaxMs());
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void clearLoginFailures(String account) {
        String key = accountKey(account);
        if (key != null) {
            limiter.clear(BUCKET_LOGIN_ACCOUNT, key);
        }
    }

    // ------------------------------------------------------------------
    // 发信 / 找回 / 重置
    // ------------------------------------------------------------------

    public void assertSendCodeAllowed(String ip) {
        if (!limiter.allow(BUCKET_SEND_CODE_IP, ip, policy.sendCodeIpLimit(),
                Duration.ofSeconds(policy.sendCodeIpWindowSeconds()))) {
            throw new RateLimitExceededException("验证码发送过于频繁，请稍后再试",
                    policy.sendCodeIpWindowSeconds());
        }
    }

    /**
     * 找回密码第一步：查询「该邮箱是否存在」。
     *
     * <p>该接口会回显用户名与游戏 ID（产品上用于让玩家确认账号），因此必须限流，
     * 否则等于提供了一个免费的账号枚举 + 身份信息接口（NS-03 / NS-11）。</p>
     */
    public void assertForgotLookupAllowed(String ip) {
        if (!limiter.allow(BUCKET_FORGOT_LOOKUP_IP, ip, policy.forgotLookupIpLimit(),
                Duration.ofSeconds(policy.forgotLookupIpWindowSeconds()))) {
            throw new RateLimitExceededException("查询过于频繁，请稍后再试",
                    policy.forgotLookupIpWindowSeconds());
        }
    }

    public void assertResetAllowed(String ip) {
        if (!limiter.allow(BUCKET_RESET_IP, ip, policy.resetIpLimit(),
                Duration.ofSeconds(policy.resetIpWindowSeconds()))) {
            throw new RateLimitExceededException("尝试过于频繁，请稍后再试",
                    policy.resetIpWindowSeconds());
        }
    }

    /** 统一收窄账号维度取值范围，避免大小写差异绕开计数。 */
    private String accountKey(String account) {
        if (account == null) {
            return null;
        }
        String value = account.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    /** 供日志排查用（不记录具体口令或验证码）。 */
    void logThrottle(String bucket, String key) {
        log.debug("[NorthStar] 限流命中 {}/{}", bucket, key);
    }
}
