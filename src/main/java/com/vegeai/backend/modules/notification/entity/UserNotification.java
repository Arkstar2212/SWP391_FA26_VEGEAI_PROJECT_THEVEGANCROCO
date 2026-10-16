package com.vegeai.backend.modules.notification.entity;

import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistent in-app activity notice owned by one recipient account.
 */
@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id,created_at"),
        @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id,read_at")
})
@Getter
@Setter
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    // Remove recipient-owned notices if an account is physically deleted.
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, length = 1200)
    private String content;

    @Column(length = 500)
    private String redirectUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;
}
