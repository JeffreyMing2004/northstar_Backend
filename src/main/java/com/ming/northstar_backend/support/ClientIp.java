package com.ming.northstar_backend.support;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 客户端真实 IP 的唯一解析入口。
 *
 * <p>限流、审计日志、桥接 IP 白名单都必须用同一个「来源 IP」，否则很容易出现
 * 「按 A 口径限流、按 B 口径记日志」导致的对不上。</p>
 *
 * <p>取值顺序：</p>
 * <ol>
 *   <li>{@code request.getRemoteAddr()} —— 生产配置了
 *       {@code server.forward-headers-strategy=framework}（见 application.properties），
 *       反代写入的 {@code X-Forwarded-For} 会被框架还原进这个字段，且反代侧是用
 *       {@code $remote_addr} <b>覆盖</b>该头，客户端无法伪造。</li>
 *   <li>只有 remoteAddr 仍是回环地址时，才退回 {@code X-Forwarded-For} /
 *       {@code X-Real-IP}（本地直连或反代未配置 forward-headers 时的兜底）。</li>
 * </ol>
 *
 * <p><b>为什么不再直接取 XFF 首段</b>：没有 realip 模块时，XFF 里是 Cloudflare
 * 边缘节点地址，而攻击者若能让反代把伪造值追加在左侧（{@code $proxy_add_x_forwarded_for}），
 * 首段就是攻击者可控的任意字符串——既能跨节点绕过限流，也能把限流计数器字典打爆。</p>
 */
public final class ClientIp {

    /** 只接受 IP 字面量：拒绝任意字符串当作限流键。 */
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final String UNKNOWN = "unknown";
    private static final int MAX_LENGTH = 45;

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String remote = sanitize(request.getRemoteAddr());
        if (remote != null && !isLoopback(remote)) {
            return remote;
        }
        String forwarded = firstValid(header(request, "X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = valid(header(request, "X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return remote != null ? remote : UNKNOWN;
    }

    private static String header(HttpServletRequest request, String name) {
        try {
            return request.getHeader(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 依次取 XFF 中从左到右第一个「看起来是 IP」的项。 */
    private static String firstValid(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        for (String part : forwardedFor.split(",")) {
            String candidate = valid(part);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String valid(String raw) {
        String value = sanitize(raw);
        if (value == null) {
            return null;
        }
        return IPV4.matcher(value).matches() || (value.contains(":") && IPV6.matcher(value).matches())
                ? value
                : null;
    }

    /** 去掉端口、IPv6 方括号，长度与字符集做白名单过滤。 */
    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value) || value.length() > MAX_LENGTH) {
            return null;
        }
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else if (value.indexOf(':') == value.lastIndexOf(':') && value.contains(":")) {
            // IPv4:port
            value = value.substring(0, value.indexOf(':'));
        }
        value = value.toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("127.");
    }
}
