package com.vegeai.backend.modules.notification.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.notification.dto.NotificationResponse;
import com.vegeai.backend.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticated REST contract for the in-app notification bell.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "18. Notifications", description = "Thông báo hoạt động dành riêng cho tài khoản đang đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications", description = "Pagination starts at page 0; size is limited to 50.")
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(notificationService.list(userId, page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get my unread notification count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal String userId) {
        return ApiResponse.success(Map.of("unreadCount", notificationService.unreadCount(userId)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        return ApiResponse.success(notificationService.markRead(userId, id));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public ApiResponse<Map<String, Integer>> markAllRead(@AuthenticationPrincipal String userId) {
        return ApiResponse.success(Map.of("updatedCount", notificationService.markAllRead(userId)));
    }
}
