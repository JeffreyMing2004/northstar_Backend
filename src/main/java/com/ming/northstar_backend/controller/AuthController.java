package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.AuthService;
import com.ming.northstar_backend.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;

    public AuthController(AuthService authService, EmailService emailService) {
        this.authService = authService;
        this.emailService = emailService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest req) {
        try {
            AuthResponse res = authService.login(req);
            return ResponseEntity.ok(ApiResponse.ok(res));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest req) {
        try {
            AuthResponse res = authService.register(req);
            return ResponseEntity.ok(ApiResponse.ok(res));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(@RequestBody SendCodeRequest req) {
        try {
            emailService.sendVerificationCode(req.getEmail().trim());
            return ResponseEntity.ok(ApiResponse.ok("验证码已发送", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        UserDto user = authService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(Authentication auth, @RequestBody UserDto update) {
        Long userId = (Long) auth.getPrincipal();
        UserDto user = authService.updateProfile(userId, update);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PostMapping("/bind-game")
    public ResponseEntity<ApiResponse<UserDto>> bindGameAccount(Authentication auth, @RequestBody BindGameRequest req) {
        Long userId = (Long) auth.getPrincipal();
        try {
            UserDto user = authService.bindGameAccount(userId, req.getMcId());
            return ResponseEntity.ok(ApiResponse.ok("游戏账号绑定成功", user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}