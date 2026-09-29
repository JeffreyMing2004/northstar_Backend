package com.ming.northstar_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 认证相关接口的限流 / 防爆破策略。
 *
 * <p>集中在一处是为了让「登录、发码、找回、重置」四条链路的阈值一眼可比——
 * 渗透报告 NS-03 / NS-08 的根因就是这几类接口各自为政，有的有限流、有的完全没有。</p>
 *
 * @param loginIpLimit                同一来源 IP 在窗口内允许的登录尝试次数
 * @param loginIpWindowSeconds        登录 IP 限流窗口长度（秒）
 * @param loginAccountFailLimit       同一账号在窗口内允许的连续失败次数，超出即短暂锁定
 * @param loginAccountWindowSeconds   账号失败计数窗口长度（秒）
 * @param loginDelayEnabled           是否启用失败后的渐进延迟（加大线上爆破的时间成本）
 * @param loginDelayMaxMs             渐进延迟上限（毫秒），避免拖住 Tomcat 线程形成自伤
 * @param sendCodeIpLimit             同一来源 IP 在窗口内允许的发信次数
 * @param sendCodeIpWindowSeconds     发信 IP 限流窗口长度（秒）
 * @param forgotLookupIpLimit         同一来源 IP 在窗口内允许的「找回身份查询」次数
 * @param forgotLookupIpWindowSeconds 找回查询限流窗口长度（秒）
 * @param resetIpLimit                同一来源 IP 在窗口内允许的重置密码提交次数
 * @param resetIpWindowSeconds        重置密码限流窗口长度（秒）
 */
@ConfigurationProperties(prefix = "northstar.auth")
public record AuthRateLimitProperties(
        @DefaultValue("30") int loginIpLimit,
        @DefaultValue("300") int loginIpWindowSeconds,
        @DefaultValue("10") int loginAccountFailLimit,
        @DefaultValue("900") int loginAccountWindowSeconds,
        @DefaultValue("true") boolean loginDelayEnabled,
        @DefaultValue("1000") long loginDelayMaxMs,
        @DefaultValue("5") int sendCodeIpLimit,
        @DefaultValue("600") int sendCodeIpWindowSeconds,
        @DefaultValue("10") int forgotLookupIpLimit,
        @DefaultValue("600") int forgotLookupIpWindowSeconds,
        @DefaultValue("10") int resetIpLimit,
        @DefaultValue("600") int resetIpWindowSeconds) {
}
