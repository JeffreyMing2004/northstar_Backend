package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.BetaPlanService;
import com.ming.northstar_backend.service.BetaService;
import com.ming.northstar_backend.service.BetaWhitelistService;
import com.ming.northstar_backend.service.RateLimitService;
import com.ming.northstar_backend.support.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/beta")
public class BetaController {

    private final BetaService betaService;
    private final BetaPlanService betaPlanService;
    private final BetaWhitelistService betaWhitelistService;
    private final RateLimitService rateLimitService;
    private final int checkLimitPerMinute;

    public BetaController(BetaService betaService, BetaPlanService betaPlanService,
                          BetaWhitelistService betaWhitelistService,
                          RateLimitService rateLimitService,
                          @Value("${northstar.public.beta-check-limit-per-minute:30}") int checkLimitPerMinute) {
        this.betaService = betaService;
        this.betaPlanService = betaPlanService;
        this.betaWhitelistService = betaWhitelistService;
        this.rateLimitService = rateLimitService;
        this.checkLimitPerMinute = checkLimitPerMinute;
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<BetaPlanDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.ok(betaPlanService.listPublicPlans()));
    }

    /**
     * 查询某个账号的内测状态（permitAll）。
     *
     * <p>该接口会明确回显「命中 / 未命中」，是账号枚举的入口之一（NS-11），
     * 因此加了一层按来源 IP 的限流；命中与否的文案保持不变以免破坏前端流程。</p>
     */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<BetaCheckResponse>> checkBeta(@RequestParam String query,
                                                                   HttpServletRequest request) {
        String ip = ClientIp.of(request);
        if (!rateLimitService.allow("beta-check-ip", ip, checkLimitPerMinute, Duration.ofMinutes(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(ApiResponse.error(429, "查询过于频繁，请稍后再试"));
        }
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
                qq, name, ClientIp.of(request), request.getHeader("User-Agent"));
        return ResponseEntity.status(outcome.httpStatus()).body(outcome.body());
    }

    /**
     * 提交内测申请。<b>必须登录</b>——申请记录挂在登录账号上，QQ / 游戏 ID 也全部
     * 从账号读取，请求体里的 {@code query} 会被忽略。
     */
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
     * 查询「我」的内测申请状态（需登录）。
     *
     * <p>前端用它把「可申请 / 审核中 / 已通过」三种界面区分开，避免已申请过的玩家
     * 反复点提交、每次都只拿到一句「你已提交过申请」。</p>
     */
    @GetMapping("/my-application")
    public ResponseEntity<ApiResponse<MyBetaApplicationDto>> myApplication(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(betaService.getMyApplication(userId)));
    }

    /**
     * 解析客户端真实 IP。统一走 {@link ClientIp}：生产配置了
     * {@code server.forward-headers-strategy=framework} 后，
     * {@code request.getRemoteAddr()} 已被框架还原为反代写入的真实来源，
     * 只有它仍是回环地址时才退回代理头解析。
     */
    static String resolveClientIp(HttpServletRequest request) {
        return ClientIp.of(request);
    }
}
