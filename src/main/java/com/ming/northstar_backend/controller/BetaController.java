package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.BetaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/beta")
public class BetaController {

    private final BetaService betaService;

    public BetaController(BetaService betaService) {
        this.betaService = betaService;
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<BetaCheckResponse>> checkBeta(@RequestParam String query) {
        BetaCheckResponse res = betaService.checkBeta(query);
        if (res == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "未找到该账号"));
        }
        return ResponseEntity.ok(ApiResponse.ok(res));
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
}
