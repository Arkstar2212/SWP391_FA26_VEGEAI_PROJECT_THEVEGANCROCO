package com.vegeai.backend.modules.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * An AI audit event an administrator has locked for investigation. Keyed by the AiAuditLog id.
 */
@Entity
@Table(name = "ai_event_locks")
@Getter
@Setter
public class AiEventLock {
    @Id
    private UUID eventId;

    private UUID lockedBy;

    @Column(length = 500)
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant lockedAt;
}
