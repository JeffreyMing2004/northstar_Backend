package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverview>> getOverview() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getOverview()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String betaStatus) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(query, betaStatus)));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        return execute(() -> ApiResponse.ok("玩家资料已更新", adminService.updateUser(id, request)));
    }

    @PostMapping("/users/{id}/admin")
    public ResponseEntity<ApiResponse<UserDto>> grantAdmin(@PathVariable Long id) {
        return execute(() -> ApiResponse.ok("管理员身份已授予", adminService.grantAdmin(id)));
    }

    @DeleteMapping("/users/{id}/admin")
    public ResponseEntity<ApiResponse<UserDto>> revokeAdmin(@PathVariable Long id) {
        return execute(() -> ApiResponse.ok("管理员身份已取消", adminService.revokeAdmin(id)));
    }

    @GetMapping("/beta-applications")
    public ResponseEntity<ApiResponse<List<BetaApplicationDto>>> getBetaApplications(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listBetaApplications(status)));
    }

    @GetMapping("/beta-plans")
    public ResponseEntity<ApiResponse<List<BetaPlanDto>>> getBetaPlans() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listBetaPlans()));
    }

    @PostMapping("/beta-plans")
    public ResponseEntity<ApiResponse<BetaPlanDto>> createBetaPlan(@RequestBody BetaPlanRequest request) {
        return execute(() -> ApiResponse.ok("内测计划已创建", adminService.createBetaPlan(request)));
    }

    @PutMapping("/beta-plans/{id}")
    public ResponseEntity<ApiResponse<BetaPlanDto>> updateBetaPlan(
            @PathVariable Long id, @RequestBody BetaPlanRequest request) {
        return execute(() -> ApiResponse.ok("内测计划已更新", adminService.updateBetaPlan(id, request)));
    }

    @PatchMapping("/beta-plans/{id}/status")
    public ResponseEntity<ApiResponse<BetaPlanDto>> updateBetaPlanStatus(
            @PathVariable Long id, @RequestBody AdminBetaPlanStatusRequest request) {
        return execute(() -> ApiResponse.ok("内测计划状态已更新",
            adminService.updateBetaPlanStatus(id, request.getStatus())));
    }

    @PostMapping("/beta-applications")
    public ResponseEntity<ApiResponse<BetaManagementResponse>> addBetaApplication(
            @RequestBody AdminBetaGrantRequest request) {
        return execute(() -> {
            BetaManagementResponse response = adminService.addBetaMember(request);
            String message = response.isEmailSent()
                ? "内测人员已添加，通过邮件已发送"
                : "内测资格已通过，但通知邮件发送失败";
            return ApiResponse.ok(message, response);
        });
    }

    @PatchMapping("/beta-applications/{id}")
    public ResponseEntity<ApiResponse<BetaManagementResponse>> decideBetaApplication(
            @PathVariable Long id, @RequestBody AdminBetaDecisionRequest request) {
        return execute(() -> {
            BetaManagementResponse response = adminService.decideBetaApplication(id, request);
            String message = response.isEmailSent()
                ? "内测申请已通过，邮件已发送"
                : response.getApplication() != null && "approved".equals(response.getApplication().getStatus())
                    ? "内测资格已通过，但通知邮件发送失败"
                    : "审核结果已保存";
            return ApiResponse.ok(message, response);
        });
    }

    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<RoomDto>>> getRooms() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listRooms()));
    }

    @GetMapping("/matches")
    public ResponseEntity<ApiResponse<List<MatchDto>>> getMatches() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listRecentMatches()));
    }

    private <T> ResponseEntity<ApiResponse<T>> execute(AdminAction<T> action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface AdminAction<T> {
        ApiResponse<T> run();
    }
}
