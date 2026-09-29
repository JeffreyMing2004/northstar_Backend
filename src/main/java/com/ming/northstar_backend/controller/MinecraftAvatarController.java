package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.MinecraftAvatarDto;
import com.ming.northstar_backend.service.MinecraftAvatarService;
import com.ming.northstar_backend.service.RateLimitService;
import com.ming.northstar_backend.support.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/minecraft/avatar")
public class MinecraftAvatarController {

    private final MinecraftAvatarService avatarService;
    private final RateLimitService rateLimitService;
    private final int limitPerMinute;

    public MinecraftAvatarController(MinecraftAvatarService avatarService,
                                     RateLimitService rateLimitService,
                                     @Value("${northstar.public.avatar-limit-per-minute:60}") int limitPerMinute) {
        this.avatarService = avatarService;
        this.rateLimitService = rateLimitService;
        this.limitPerMinute = limitPerMinute;
    }

    /**
     * 查询玩家皮肤。
     *
     * <p>该接口 permitAll 且每次未命中缓存都会外呼 Mojang 并写 Redis（NS-14）。
     * 本身不存在 SSRF（入参已 URLEncoder），但可被用来消耗出口带宽与 Redis 空间，
     * 因此加一层按来源 IP 的限流。</p>
     */
    @GetMapping("/{playerId}")
    public ResponseEntity<ApiResponse<MinecraftAvatarDto>> getAvatar(@PathVariable String playerId,
                                                                    HttpServletRequest request) {
        if (!rateLimitService.allow("minecraft-avatar-ip", ClientIp.of(request), limitPerMinute, Duration.ofMinutes(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(ApiResponse.error(429, "请求过于频繁，请稍后再试"));
        }
        return ResponseEntity.ok(ApiResponse.ok(avatarService.resolve(playerId)));
    }
}
