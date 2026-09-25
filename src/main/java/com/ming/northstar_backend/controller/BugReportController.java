package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.BugReportDto;
import com.ming.northstar_backend.dto.BugReportRequest;
import com.ming.northstar_backend.service.BugReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class BugReportController {

    private final BugReportService bugReportService;

    public BugReportController(BugReportService bugReportService) {
        this.bugReportService = bugReportService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BugReportDto>> submit(Authentication auth,
                                                            @RequestBody BugReportRequest req) {
        Long userId = (auth != null && auth.getPrincipal() instanceof Long)
                ? (Long) auth.getPrincipal()
                : null;
        try {
            BugReportDto dto = bugReportService.submit(userId, req);
            return ResponseEntity.ok(ApiResponse.ok("反馈提交成功", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}