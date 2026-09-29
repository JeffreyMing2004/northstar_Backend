package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.AuthGuard;
import com.ming.northstar_backend.service.AuthService;
import com.ming.northstar_backend.service.EmailService;
import com.ming.northstar_backend.support.ClientIp;
import com.ming.northstar_backend.support.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailService emailService;
    private final AuthGuard authGuard;

    public AuthController(AuthService authService, EmailService emailService, AuthGuard authGuard) {
        this.authService = authService;
        this.emailService = emailService;
        this.authGuard = authGuard;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest req,
                                                          HttpServletRequest request) {
        try {
            AuthResponse res = authService.login(req, ClientIp.of(request));
            return ResponseEntity.ok(ApiResponse.ok(res));
        } catch (RateLimitExceededException e) {
            return tooManyRequests(e);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest req) {
        try {
            AuthResponse res = authService.register(req);
            return ResponseEntity.ok(ApiResponse.ok(res));
        } catch (RateLimitExceededException e) {
            return tooManyRequests(e);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 发送邮箱验证码。
     *
     * <p>限流从「只按邮箱算冷却」升级为「按来源 IP 限流 + 按邮箱冷却」：
     * 原实现换个邮箱就能继续发，等于开放了邮件轰炸（NS-08）。</p>
     */
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponse<Void>> sendCode(@RequestBody SendCodeRequest req,
                                                      HttpServletRequest request) {
        try {
            authGuard.assertSendCodeAllowed(ClientIp.of(request));
            String email = req.getEmail() == null ? "" : req.getEmail().trim();
            emailService.sendVerificationCode(email, req.getPurpose());
            return ResponseEntity.ok(ApiResponse.ok("验证码已发送", null));
        } catch (RateLimitExceededException e) {
            return tooManyRequests(e);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** 忘记密码第一步：按邮箱检索账号身份，回显用户名与 MC ID 供用户确认。 */
    @PostMapping("/forgot-password/lookup")
    public ResponseEntity<ApiResponse<ForgotIdentityDto>> forgotLookup(@RequestBody ForgotPasswordLookupRequest req,
                                                                      HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    authService.lookupForgotIdentity(req.getEmail(), ClientIp.of(request))));
        } catch (RateLimitExceededException e) {
            return tooManyRequests(e);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /** 忘记密码最后一步：校验验证码并写入新密码，成功后直接签发 token 免二次登录。 */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<AuthResponse>> resetPassword(@RequestBody ResetPasswordRequest req,
                                                                  HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok("密码已重置",
                    authService.resetPassword(req, ClientIp.of(request))));
        } catch (RateLimitExceededException e) {
            return tooManyRequests(e);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            return ResponseEntity.ok(ApiResponse.ok(authService.getProfile(userId)));
        } catch (RuntimeException e) {
            // token 指向的用户已不存在（例如切换/重置数据库后旧 token 失效）。
            // 返回 401 让前端拦截器自动登出并跳转登录页，而不是停在报错页面。
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "登录状态已失效，请重新登录"));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(Authentication auth, @RequestBody UserDto update) {
        Long userId = (Long) auth.getPrincipal();
        try {
            UserDto user = authService.updateProfile(userId, update);
            return ResponseEntity.ok(ApiResponse.ok(user));
        } catch (RuntimeException e) {
            // 例如「QQ 号已绑定，绑定后不可更改」。这条消息必须原样回到前端，
            // 否则会落进 Spring 默认错误响应，玩家只看到一句没有信息量的「更新失败」。
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
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

    /** 限流统一回 429，并带上 Retry-After，方便前端提示「请稍后再试」。 */
    private <T> ResponseEntity<ApiResponse<T>> tooManyRequests(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(ApiResponse.error(429, e.getMessage()));
    }
}
