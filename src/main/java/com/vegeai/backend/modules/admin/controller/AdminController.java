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
@Tag(name = "Admin", description = "Admin-only user governance, system telemetry, and AI Ops monitoring.")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "List and search users",
               description = "Filter by keyword (username, email, fullName), role, or status with pagination and sorting.")
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
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable UUID userId) {
        UserProfileResponse user = adminService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PatchMapping("/users/{userId}/status")
    @Operation(summary = "Update user account status",
               description = "Locks, unlocks, activates or disables an account. Protected by Admin Immunity Rule.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest req,
            @AuthenticationPrincipal String adminCallerId) {

        UserProfileResponse updated = adminService.updateUserStatus(userId, req, adminCallerId);
        return ResponseEntity.ok(ApiResponse.success("Account status updated to " + req.getStatus(), updated));
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "Change user role",
               description = "Promote a member to ADMIN or demote to MEMBER. Self-demotion is prevented.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRoleRequest req,
            @AuthenticationPrincipal String adminCallerId) {

        UserProfileResponse updated = adminService.updateUserRole(userId, req, adminCallerId);
        return ResponseEntity.ok(ApiResponse.success("User role updated to " + req.getRole(), updated));
    }

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get admin dashboard overview statistics")
    public ResponseEntity<ApiResponse<AdminDashboardStatsResponse>> getDashboardStats() {
        AdminDashboardStatsResponse stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/ai/status")
    @Operation(summary = "Get AI Ops status and model telemetry",
               description = "Monitors external LLMs (Gemini, Whisper) and local guardrails latency, uptime, and request quotas.")
    public ResponseEntity<ApiResponse<AiOpsStatusResponse>> getAiOpsStatus() {
        AiOpsStatusResponse status = adminService.getAiOpsStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @GetMapping("/ai/audit-logs")
    @Operation(summary = "Get AI audit trail logs",
               description = "Returns audit logs of recent AI interactions, model latency, and moderation flags.")
    public ResponseEntity<ApiResponse<List<AiAuditLogEntry>>> getAiAuditLogs() {
        List<AiAuditLogEntry> logs = adminService.getAiAuditLogs();
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
