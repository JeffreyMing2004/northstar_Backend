package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.ServerStatsReport;
import com.ming.northstar_backend.service.LiveServerStatsService;
import com.ming.northstar_backend.service.BridgeRoomSyncService;
import com.ming.northstar_backend.support.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * MC 插件 → 后端的桥接上报接口。
 *
 * <p>对应渗透报告 NS-05：原先只有一个默认值 {@code local-bridge-secret} 作为凭据，
 * 一旦沿用默认值就等于不设防。现在：</p>
 * <ol>
 *   <li>共享令牌不再有默认值，缺失或过弱会导致应用直接启动失败
 *       （见 {@code SecurityStartupValidator}）；</li>
 *   <li>可选启用来源 IP 白名单 {@code northstar.bridge.allowed-ips}，
 *       支持单个 IP 与 IPv4 CIDR，逗号分隔；留空表示不限制；</li>
 *   <li>令牌比较继续用 {@link MessageDigest#isEqual} 做恒定时间比较
 *       （顺带兼容长度不等，不会因提前返回泄露前缀）。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/bridge")
public class ServerStatsController {
    private final LiveServerStatsService serverStatsService;
    private final BridgeRoomSyncService roomSyncService;
    private final String bridgeSecret;
    private final List<String> allowedIps;

    public ServerStatsController(
            LiveServerStatsService serverStatsService,
            BridgeRoomSyncService roomSyncService,
            @Value("${northstar.bridge.secret:}") String bridgeSecret,
            @Value("${northstar.bridge.allowed-ips:}") String allowedIps) {
        this.serverStatsService = serverStatsService;
        this.roomSyncService = roomSyncService;
        this.bridgeSecret = bridgeSecret;
        this.allowedIps = Arrays.stream(allowedIps == null ? new String[0] : allowedIps.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @PostMapping("/stats")
    public ResponseEntity<ApiResponse<String>> reportStats(
            @RequestHeader(value = "X-Northstar-Bridge-Token", required = false) String token,
            @Valid @RequestBody ServerStatsReport report,
            HttpServletRequest request) {
        if (!isAllowedSource(ClientIp.of(request))) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "来源 IP 不在桥接白名单内"));
        }
        if (!isValidToken(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "桥接密钥无效"));
        }
        try {
            roomSyncService.syncRooms(report.getServerId().trim(), report.getRooms());
            serverStatsService.update(report);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, exception.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.ok("服务器状态已更新", "ok"));
    }

    private boolean isValidToken(String token) {
        if (bridgeSecret == null || bridgeSecret.isBlank()) {
            // 正常启动流程下不会走到这里（SecurityStartupValidator 会拦下）；
            // 兜底成「一律拒绝」而不是「一律放行」。
            return false;
        }
        return MessageDigest.isEqual(
                bridgeSecret.getBytes(StandardCharsets.UTF_8),
                (token == null ? "" : token).getBytes(StandardCharsets.UTF_8)
        );
    }

    /** 白名单为空表示不限制来源（仍需令牌正确）。 */
    private boolean isAllowedSource(String ip) {
        if (allowedIps.isEmpty()) {
            return true;
        }
        String value = ip == null ? "" : ip.toLowerCase(Locale.ROOT);
        for (String rule : allowedIps) {
            String candidate = rule.toLowerCase(Locale.ROOT);
            if (candidate.contains("/")) {
                if (matchesCidr(value, candidate)) {
                    return true;
                }
            } else if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 仅支持 IPv4 CIDR；IPv6 请用精确匹配。 */
    private boolean matchesCidr(String ip, String cidr) {
        int slash = cidr.indexOf('/');
        String network = cidr.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        Long address = toIpv4(ip);
        Long base = toIpv4(network);
        if (address == null || base == null || prefix < 0 || prefix > 32) {
            return false;
        }
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (address & mask) == (base & mask);
    }

    private Long toIpv4(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        long result = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octet < 0 || octet > 255) {
                return null;
            }
            result = (result << 8) | octet;
        }
        return result;
    }
}
