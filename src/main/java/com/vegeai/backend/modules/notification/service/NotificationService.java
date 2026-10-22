package com.vegeai.backend.modules.notification.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.notification.entity.Notification;
import com.vegeai.backend.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Slf4j
public class NotificationService {
    public static final String BLOG_APPROVED = "BLOG_APPROVED";
    public static final String BLOG_REJECTED = "BLOG_REJECTED";
    public static final String BLOG_AI_FLAGGED = "BLOG_AI_FLAGGED";
    public static final String BLOG_PENDING_ADMIN = "BLOG_PENDING_ADMIN";
    public static final String NEW_COMMENT = "NEW_COMMENT";
    public static final String QUESTION_ANSWERED = "QUESTION_ANSWERED";
    public static final String FRIDGE_EXPIRING = "FRIDGE_EXPIRING";
    public static final String FRIDGE_EXPIRED = "FRIDGE_EXPIRED";
    public static final String FRIDGE_UPDATED = "FRIDGE_UPDATED";

    public record NotificationResponse(UUID id, String eventType, String title, String content,
                                       LocalDateTime createdAt, LocalDateTime readAt, boolean isRead, String redirectUrl) {}

    private final NotificationRepository notifications;
    private final FridgeItemRepository fridge;

    /**
     * Fire-and-forget: a notification failure must never break the business action that triggered it,
     * so this runs in its own transaction and swallows (but logs) every error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(UUID userId, String eventType, String title, String content, String redirectUrl, String dedupeKey) {
        try { create(userId, eventType, title, content, redirectUrl, dedupeKey); }
        catch (RuntimeException ex) { log.warn("Could not create notification {} for user {}: {}", eventType, userId, ex.getMessage()); }
    }

    private void create(UUID userId, String eventType, String title, String content, String redirectUrl, String dedupeKey) {
        if (userId == null) return;
        if (dedupeKey != null && notifications.existsByUserIdAndDedupeKey(userId, dedupeKey)) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setEventType(eventType);
        n.setTitle(cut(title, 160));
        n.setContent(cut(content, 1000));
        n.setRedirectUrl(redirectUrl != null && redirectUrl.startsWith("/") && !redirectUrl.startsWith("//") ? cut(redirectUrl, 300) : null);
        n.setDedupeKey(dedupeKey);
        notifications.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String principal, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new AppException(ErrorCode.VALIDATION_ERROR, "page must be >= 0; size must be between 1 and 50.");
        return PageResponse.from(notifications.findByUserIdOrderByCreatedAtDesc(user(principal), PageRequest.of(page, size)).map(this::response));
    }

    @Transactional
    public long unreadCount(String principal) {
        UUID userId = user(principal);
        syncFridgeAlerts(userId);
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(String principal, UUID id) {
        Notification n = notifications.findByIdAndUserId(id, user(principal)).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (n.getReadAt() == null) n.setReadAt(LocalDateTime.now());
        return response(n);
    }

    @Transactional
    public int markAllRead(String principal) { return notifications.markAllRead(user(principal), LocalDateTime.now()); }

    /** Creates (idempotent) alerts for this user's fridge items that are about to expire or already expired. */
    @Transactional
    public void syncFridgeAlerts(UUID userId) {
        try {
            for (FridgeItem item : fridge.findByUserIdAndStatusAndExpiryDateLessThanEqualOrderByExpiryDateAsc(
                    userId, FridgeItemStatus.ACTIVE, LocalDate.now().plusDays(2))) alertFor(item);
        } catch (RuntimeException ex) { log.warn("Fridge alert sync failed for {}: {}", userId, ex.getMessage()); }
    }

    /** Hourly sweep so alerts exist even when the user is not browsing the app. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void sweepFridgeAlerts() {
        try { fridge.findByStatusAndExpiryDateLessThanEqual(FridgeItemStatus.ACTIVE, LocalDate.now().plusDays(2)).forEach(this::alertFor); }
        catch (RuntimeException ex) { log.warn("Fridge alert sweep failed: {}", ex.getMessage()); }
    }

    private void alertFor(FridgeItem item) {
        long days = item.calculateDaysRemaining();
        String key = (days < 0 ? "FRIDGE_EXPIRED:" : "FRIDGE_EXPIRING:") + item.getItemId() + ":" + item.getExpiryDate();
        if (days < 0) {
            create(item.getUserId(), FRIDGE_EXPIRED, item.getName() + " đã quá hạn",
                    "Nguyên liệu \"" + item.getName() + "\" đã quá hạn " + Math.abs(days) + " ngày. Hãy kiểm tra và bỏ nếu không còn an toàn, hoặc cập nhật lại hạn dùng nếu bạn vừa mua mới.", "/fridge", key);
        } else {
            create(item.getUserId(), FRIDGE_EXPIRING, item.getName() + " sắp hết hạn",
                    "Nguyên liệu \"" + item.getName() + "\" còn " + days + " ngày. Hãy ưu tiên nấu sớm — mở \"Nấu gì từ tủ lạnh?\" để lấy gợi ý.", "/fridge", key);
        }
    }

    private UUID user(String principal) {
        try { return UUID.fromString(principal); }
        catch (IllegalArgumentException | NullPointerException ex) { throw new AppException(ErrorCode.UNAUTHORIZED); }
    }

    private NotificationResponse response(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventType(), n.getTitle(), n.getContent(), n.getCreatedAt(), n.getReadAt(), n.getReadAt() != null, n.getRedirectUrl());
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        String v = value.strip();
        return v.length() <= max ? v : v.substring(0, max - 1) + "…";
    }
}
