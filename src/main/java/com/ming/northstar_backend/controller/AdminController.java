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

    @GetMapping("/beta-applications")
    public ResponseEntity<ApiResponse<List<BetaApplicationDto>>> getBetaApplications(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listBetaApplications(status)));
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

    @PatchMapping("/rooms/{id}")
    public ResponseEntity<ApiResponse<RoomDto>> updateRoom(
            @PathVariable Long id, @RequestBody AdminRoomStatusRequest request) {
        return execute(() -> ApiResponse.ok("房间状态已更新", adminService.updateRoomStatus(id, request)));
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Long id) {
        return execute(() -> {
            adminService.deleteRoom(id);
            return ApiResponse.ok("房间已删除", null);
        });
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
