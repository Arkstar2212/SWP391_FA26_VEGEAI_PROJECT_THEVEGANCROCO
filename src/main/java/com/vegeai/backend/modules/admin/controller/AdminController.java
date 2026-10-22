package com.vegeai.backend.modules.admin.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.admin.dto.*;
import com.vegeai.backend.modules.admin.service.AdminService;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Super Admin Control Center.
 * All endpoints require ROLE_ADMIN (enforced in SecurityConfig + @PreAuthorize).
 *
 * Implements:
 *  - User listing with multi-field search and status/role filtering
 *  - User status updating with Admin Immunity Rule enforcement
 *  - Role management
 *  - Dashboard statistics telemetry
 *  - AI Ops health monitoring and audit logging
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "03. Super Admin & Governance", description = "Admin-only user governance, system telemetry, and AI Ops monitoring.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "List and search users", description = """
            **Tìm kiếm và lọc danh sách người dùng toàn hệ thống (Phân trang).**
            
            **Tham số truy vấn (Query Params):**
            - `page`: Số trang bắt đầu từ `0` (Mặc định: 0).
            - `size`: Số bản ghi mỗi trang (Mặc định: 20).
            - `search`: Từ khóa tìm kiếm theo `username`, `email` hoặc `fullName`.
            - `role`: Lọc theo vai trò (`ADMIN` hoặc `MEMBER`).
            - `status`: Lọc theo trạng thái (`ACTIVE`, `PENDING_VERIFICATION`, `LOCKED`, `DISABLED`).
            - `sortBy`: Cột sắp xếp (`createdAt`, `username`, `email`, `lastLogin`).
            - `direction`: Hướng sắp xếp (`asc` hoặc `desc`).
            """)
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> listUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String search,
            @RequestParam(required = false)    UserRole role,
            @RequestParam(required = false)    AccountStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String direction) {

        PageResponse<UserProfileResponse> result = adminService.listUsers(
                page, size, search, role, status, sortBy, direction);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user details by ID", description = """
            **Xem chi tiết thông tin quản trị của người dùng theo UUID.**
            
            - Yêu cầu quyền ADMIN.
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable UUID userId) {
        UserProfileResponse user = adminService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Update user account status", description = """
            **Khóa hoặc mở khóa trạng thái tài khoản.**
            
            **Quy tắc:**
            - `status`: Trạng thái mới (`ACTIVE`, `LOCKED`, `DISABLED`, `PENDING_VERIFICATION`).
            - `reason`: Lý do thay đổi trạng thái (Ví dụ: "Vi phạm quy chuẩn cộng đồng").
            - **Admin Immunity Rule:** Hệ thống chặn tuyệt đối hành vi khóa/vô hiệu hóa tài khoản của chính Admin đang thao tác hoặc Super Admin gốc.
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest req,
            @AuthenticationPrincipal String adminCallerId) {

        UserProfileResponse updated = adminService.updateUserStatus(userId, req, adminCallerId);
        return ResponseEntity.ok(ApiResponse.success("Account status updated to " + req.getStatus(), updated));
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "Change user role", description = """
            **Phân quyền vai trò người dùng.**
            
            - `role`: Vai trò mới (`ADMIN` hoặc `MEMBER`).
            - Hệ thống chặn việc Admin tự hạ quyền (Self-demotion) của chính mình.
            """)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRoleRequest req,
            @AuthenticationPrincipal String adminCallerId) {

        UserProfileResponse updated = adminService.updateUserRole(userId, req, adminCallerId);
        return ResponseEntity.ok(ApiResponse.success("User role updated to " + req.getRole(), updated));
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get admin dashboard overview statistics", description = """
            **Báo cáo thống kê tổng quan hệ thống.**
            
            - Trả về tổng số thành viên, số tài khoản đang hoạt động, tài khoản bị khóa, và tỷ lệ kích hoạt tài khoản.
            """)
    public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> getDashboardStats() {
        AdminDashboardStatsResponse stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/ai/status")
    @Operation(summary = "Get AI Ops status and model telemetry", description = """
            **Giám sát số liệu vận hành được ghi lại từ các yêu cầu AI thực tế.**

            - Tổng hợp lượt gọi, tỷ lệ thành công và độ trễ trong 24 giờ gần nhất.
            """)
    public ResponseEntity<ApiResponse<AiOpsStatusResponse>> getAiOpsStatus() {
        AiOpsStatusResponse status = adminService.getAiOpsStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/ai/audit-logs")
    @Operation(summary = "Get AI audit trail logs", description = """
            **Xem các sự kiện gọi AI gần nhất.**

            - Log không lưu nguyên câu hỏi, prompt hoặc dữ liệu cá nhân của người dùng.
            """)
    public ResponseEntity<ApiResponse<List<AiAuditLogEntry>>> getAiAuditLogs() {
        List<AiAuditLogEntry> logs = adminService.getAiAuditLogs();
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
