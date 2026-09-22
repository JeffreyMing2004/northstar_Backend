package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.MinecraftAvatarDto;
import com.ming.northstar_backend.service.MinecraftAvatarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/minecraft/avatar")
public class MinecraftAvatarController {

    private final MinecraftAvatarService avatarService;

    public MinecraftAvatarController(MinecraftAvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<ApiResponse<MinecraftAvatarDto>> getAvatar(@PathVariable String playerId) {
        return ResponseEntity.ok(ApiResponse.ok(avatarService.resolve(playerId)));
    }
}
