package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.ServerStatsReport;
import com.ming.northstar_backend.service.LiveServerStatsService;
import com.ming.northstar_backend.service.BridgeRoomSyncService;
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

@RestController
@RequestMapping("/api/bridge")
public class ServerStatsController {
    private final LiveServerStatsService serverStatsService;
    private final BridgeRoomSyncService roomSyncService;
    private final String bridgeSecret;

    public ServerStatsController(
            LiveServerStatsService serverStatsService,
            BridgeRoomSyncService roomSyncService,
            @Value("${northstar.bridge.secret}") String bridgeSecret) {
        this.serverStatsService = serverStatsService;
        this.roomSyncService = roomSyncService;
        this.bridgeSecret = bridgeSecret;
    }

    @PostMapping("/stats")
    public ResponseEntity<ApiResponse<String>> reportStats(
            @RequestHeader(value = "X-Northstar-Bridge-Token", required = false) String token,
            @Valid @RequestBody ServerStatsReport report) {
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
        return bridgeSecret != null
                && !bridgeSecret.isBlank()
                && MessageDigest.isEqual(
                        bridgeSecret.getBytes(StandardCharsets.UTF_8),
                        (token == null ? "" : token).getBytes(StandardCharsets.UTF_8)
                );
    }
}
