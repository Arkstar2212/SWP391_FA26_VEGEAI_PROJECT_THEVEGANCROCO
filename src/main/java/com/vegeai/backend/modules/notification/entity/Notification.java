package com.vegeai.backend.modules.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** In-app notification addressed to exactly one user. */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_user_dedupe", columnNames = {"user_id", "dedupe_key"}),
        indexes = @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at"))
@Getter @Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "notification_id")
    private UUID id;

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String content;
    @Column(name = "redirect_url", length = 300) private String redirectUrl;
    /** Idempotency key so periodic jobs never create the same alert twice. Null = always insert. */
    @Column(name = "dedupe_key", length = 160) private String dedupeKey;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "read_at") private LocalDateTime readAt;
}
