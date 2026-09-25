package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.ApiResponse;
import com.ming.northstar_backend.dto.BugReportDto;
import com.ming.northstar_backend.dto.BugReportProcessRequest;
import com.ming.northstar_backend.service.BugReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final BugReportService bugReportService;

    public AdminFeedbackController(BugReportService bugReportService) {
        this.bugReportService = bugReportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BugReportDto>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(bugReportService.listAll()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<BugReportDto>> process(@PathVariable Long id,
                                                             @RequestBody BugReportProcessRequest request,
                                                             Authentication auth) {
        try {
            String admin = auth.getDetails() instanceof String name ? name : "管理员";
            BugReportDto dto = bugReportService.process(id, request, admin);
            return ResponseEntity.ok(ApiResponse.ok("反馈已更新", dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
