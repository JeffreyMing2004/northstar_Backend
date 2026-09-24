package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.BetaPlanService;
import com.ming.northstar_backend.service.BetaService;
import com.ming.northstar_backend.service.BetaWhitelistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beta")
public class BetaController {

    private final BetaService betaService;
    private final BetaPlanService betaPlanService;
    private final BetaWhitelistService betaWhitelistService;

    public BetaController(BetaService betaService, BetaPlanService betaPlanService,
                          BetaWhitelistService betaWhitelistService) {
        this.betaService = betaService;
        this.betaPlanService = betaPlanService;
        this.betaWhitelistService = betaWhitelistService;
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<BetaPlanDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.ok(betaPlanService.listPublicPlans()));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<BetaCheckResponse>> checkBeta(@RequestParam String query) {
        BetaCheckResponse res = betaService.checkBeta(query);
        if (res == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "未找到该账号"));
        }
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    /**
     * 接口 A：客户端 Mod 内测资格校验。
     *
     * <pre>{@code
     * GET /api/beta/verify?qq=123456789&name=Steve
     * }</pre>
     *
     * <p>身份只用「QQ + 游戏ID（{@code name}）」判定。北极战区是离线模式服务器，玩家 UUID
     * 由启动器自行生成、每次启动都可能变化，服务端无法据此识别玩家，因此本接口不接受
     * 也不使用 {@code uuid}（旧版客户端多传的 {@code uuid} 会被 Spring 直接忽略）。</p>
     *
     * <p>响应体为专用的 {@link BetaVerifyResponse}（同时含 {@code success} 与 {@code code}），
     * <b>不使用</b> {@link ApiResponse} 包装——客户端只认这两套字段，套一层 {@code data}
     * 会导致判定失败。</p>
     *
     * <p>HTTP 状态码语义：2xx 表示判定有效（通过 / 未通过都算），非 2xx 表示服务不可用，
     * 客户端会停留在验证界面允许玩家重试而不崩溃。</p>
     */
    @GetMapping("/verify")
    public ResponseEntity<BetaVerifyResponse> verifyBeta(
            @RequestParam(value = "qq", required = false) String qq,
            @RequestParam(value = "name", required = false) String name,
            HttpServletRequest request) {
        BetaWhitelistService.VerifyOutcome outcome = betaWhitelistService.verify(
                qq, name, resolveClientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.body());
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<String>> applyForBeta(Authentication auth, @RequestBody BetaApplyRequest req) {
        Long userId = (Long) auth.getPrincipal();
        try {
            betaService.applyForBeta(userId, req);
            return ResponseEntity.ok(ApiResponse.ok("申请提交成功", "ok"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 解析客户端真实 IP。生产环境前面有 Nginx / OpenResty，直连地址恒为 127.0.0.1，
     * 因此优先取反向代理写入的请求头。
     */
    static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
