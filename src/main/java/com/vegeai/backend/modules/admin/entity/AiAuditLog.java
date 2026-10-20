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

@Entity
@Table(name = "ai_audit_logs", indexes = {
        @Index(name = "idx_ai_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_ai_audit_model_created", columnList = "model,created_at")
})
@Getter
@Setter
public class AiAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 40)
    private String outcome;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false, length = 20)
    private String userType;

    @Column(length = 120)
    private String errorType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
