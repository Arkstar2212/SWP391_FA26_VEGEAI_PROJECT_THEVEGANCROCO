package com.vegeai.backend.modules.admin.repository;

import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {
    long countByCreatedAtAfter(Instant from);

    long countByCreatedAtAfterAndStatus(Instant from, String status);

    long countByCreatedAtAfterAndUserType(Instant from, String userType);

    @Query("select coalesce(avg(log.latencyMs), 0) from AiAuditLog log where log.createdAt >= :from")
    double averageLatencySince(@Param("from") Instant from);

    @Query("select coalesce(avg(log.latencyMs), 0) from AiAuditLog log where log.createdAt >= :from and log.model = :model")
    double averageLatencySinceForModel(@Param("from") Instant from, @Param("model") String model);

    List<AiAuditLog> findTop50ByOrderByCreatedAtDesc();

    AiAuditLog findFirstByModelAndCreatedAtAfterOrderByCreatedAtDesc(String model, Instant from);
}
