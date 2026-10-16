package com.vegeai.backend.modules.notification.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.notification.dto.NotificationResponse;
import com.vegeai.backend.modules.notification.entity.UserNotification;
import com.vegeai.backend.modules.notification.repository.UserNotificationRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores activity notices and scopes every read/update to the authenticated recipient.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserNotificationRepository notifications;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String principal, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Page must be non-negative and size must be between 1 and 50.");
        }
        UUID userId = parseUserId(principal);
        Page<UserNotification> result = notifications.findByRecipient_UserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.from(result.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String principal) {
        return notifications.countByRecipient_UserIdAndReadAtIsNull(parseUserId(principal));
    }

    @Transactional
    public NotificationResponse markRead(String principal, UUID notificationId) {
        UserNotification notification = notifications.findByIdAndRecipient_UserId(notificationId, parseUserId(principal))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notifications.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(String principal) {
        return notifications.markAllReadByRecipient(parseUserId(principal), LocalDateTime.now());
    }

    /**
     * Adds an in-app notice inside the caller's transaction so failed activity cannot leave a false notice.
     */
    @Transactional
    public void notifyUser(User recipient, String eventType, String title, String content, String redirectUrl) {
        UserNotification notification = new UserNotification();
        notification.setRecipient(recipient);
        notification.setEventType(eventType);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRedirectUrl(redirectUrl);
        notifications.save(notification);
    }

    /**
     * Sends one activity notice to each active administrator account for moderation queues.
     */
    @Transactional
    public void notifyAdministrators(String eventType, String title, String content, String redirectUrl) {
        users.findAllByRoleAndStatus(UserRole.ADMIN, AccountStatus.ACTIVE).forEach(admin ->
                notifyUser(admin, eventType, title, content, redirectUrl));
    }

    private UUID parseUserId(String principal) {
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
