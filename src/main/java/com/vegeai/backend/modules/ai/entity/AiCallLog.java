package com.vegeai.backend.modules.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_call_logs", indexes = @Index(name = "idx_ai_call_logs_created_at", columnList = "created_at"))
@Getter
@Setter
public class AiCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "error_type", length = 40)
    private String errorType;
}
