package com.ming.northstar_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 传输层安全策略：安全响应头 + Cookie 加固 + 可选 HTTP→HTTPS 跳转。
 *
 * <p>对应渗透报告 NS-06：全站缺 HSTS / CSP / nosniff / X-Frame-Options / Referrer-Policy /
 * Permissions-Policy，{@code p_uv_id} 这类 Cookie 也没有 {@code Secure} 和 {@code SameSite}。</p>
 *
 * <p>注意分工：<b>静态前端由 OpenResty 直接返回，不经过本后端</b>，所以真正的 CSP
 * 必须在 nginx 侧配置（见 docs/SECURITY-REMEDIATION-20260929.md 的运维清单）。
 * 本过滤器负责 API 响应与任何由 Spring 渲染的页面（例如 {@code /error}），
 * 同时兜住「Cookie 由后端设置」的情况。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final long hstsMaxAgeSeconds;
    private final boolean hstsIncludeSubDomains;
    private final String contentSecurityPolicy;
    private final boolean forceHttps;

    public SecurityHeadersFilter(
            @Value("${northstar.security.headers.enabled:true}") boolean enabled,
            @Value("${northstar.security.hsts-max-age-seconds:2592000}") long hstsMaxAgeSeconds,
            @Value("${northstar.security.hsts-include-subdomains:true}") boolean hstsIncludeSubDomains,
            @Value("${northstar.security.content-security-policy:default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'}")
            String contentSecurityPolicy,
            @Value("${northstar.security.force-https:false}") boolean forceHttps) {
        this.enabled = enabled;
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
        this.contentSecurityPolicy = contentSecurityPolicy;
        this.forceHttps = forceHttps;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        boolean secure = isSecure(request);
        if (forceHttps && !secure && shouldRedirect(request)) {
            redirectToHttps(request, response);
            return;
        }

        applyHeaders(response, secure);
        chain.doFilter(request, new HardeningResponseWrapper(response, secure));
    }

    private void applyHeaders(HttpServletResponse response, boolean secure) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=()");
        response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // HSTS 只在确实走 HTTPS 时下发：在 http://localhost 上下发会把浏览器
        // 锁死到 https，本地调试会直接打不开站点。
        if (secure && hstsMaxAgeSeconds > 0) {
            String value = "max-age=" + hstsMaxAgeSeconds
                    + (hstsIncludeSubDomains ? "; includeSubDomains" : "");
            response.setHeader("Strict-Transport-Security", value);
        }
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.toLowerCase(Locale.ROOT).contains("https");
    }

    /** 只对「看起来是公网域名」的请求跳转，避免把本机 IP 访问也跳走。 */
    private boolean shouldRedirect(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String name = host.toLowerCase(Locale.ROOT);
        String bare = name.contains(":") ? name.substring(0, name.indexOf(':')) : name;
        return !(bare.equals("localhost") || bare.equals("127.0.0.1") || bare.equals("::1")
                || bare.endsWith(".local") || bare.startsWith("192.168.") || bare.startsWith("10."));
    }

    private void redirectToHttps(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder target = new StringBuilder("https://").append(request.getHeader("Host"))
                .append(request.getRequestURI());
        if (request.getQueryString() != null) {
            target.append('?').append(request.getQueryString());
        }
        response.setStatus(308);
        response.setHeader("Location", target.toString());
    }

    /** 给任何由后端下发的 Set-Cookie 补上 Secure / SameSite（已显式设置的不覆盖）。 */
    private static final class HardeningResponseWrapper extends HttpServletResponseWrapper {

        private final boolean secure;

        HardeningResponseWrapper(HttpServletResponse response, boolean secure) {
            super(response);
            this.secure = secure;
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, harden(name, value));
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, harden(name, value));
        }

        private String harden(String name, String value) {
            if (name == null || value == null || !"Set-Cookie".equalsIgnoreCase(name)) {
                return value;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            String result = value;
            if (secure && !lower.contains("; secure")) {
                result = result + "; Secure";
            }
            if (!lower.contains("samesite")) {
                result = result + "; SameSite=Lax";
            }
            return result;
        }
    }
}
