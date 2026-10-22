package com.vegeai.backend.modules.notification.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.notification.service.NotificationService.NotificationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
@Tag(name = "16. Notifications", description = "Private in-app notifications for the signed-in user.")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(@AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(userId, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(@AuthenticationPrincipal String userId) {
        return ApiResponse.success(Map.of("unreadCount", service.unreadCount(userId)));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> read(@AuthenticationPrincipal String userId, @PathVariable UUID id) {
        return ApiResponse.success(service.markRead(userId, id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll(@AuthenticationPrincipal String userId) {
        return ApiResponse.success(Map.of("updatedCount", service.markAllRead(userId)));
    }
}
