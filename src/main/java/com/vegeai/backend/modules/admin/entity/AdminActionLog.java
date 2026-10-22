package com.vegeai.backend.modules.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail for administrator actions taken from the AI monitoring dashboard.
 */
@Entity
@Table(name = "admin_action_logs", indexes = @Index(name = "idx_admin_action_created_at", columnList = "created_at"))
@Getter
@Setter
public class AdminActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID adminId;

    @Column(nullable = false, length = 40)
    private String action;      // AI_EVENT_LOCKED, AI_EVENT_UNLOCKED, AI_REPLAY_TEST

    @Column(nullable = false, length = 30)
    private String targetType;  // AI_EVENT

    @Column(nullable = false, length = 64)
    private String targetId;

    @Column(length = 500)
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
