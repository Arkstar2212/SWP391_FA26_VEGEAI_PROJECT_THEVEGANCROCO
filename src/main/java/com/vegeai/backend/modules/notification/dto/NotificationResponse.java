package com.vegeai.backend.modules.notification.dto;

import com.vegeai.backend.modules.notification.entity.UserNotification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public notification payload; recipient account details are never exposed.
 */
public record NotificationResponse(
        UUID id,
        String eventType,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        boolean isRead,
        String redirectUrl
) {
    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getReadAt() != null,
                notification.getRedirectUrl()
        );
    }
}
