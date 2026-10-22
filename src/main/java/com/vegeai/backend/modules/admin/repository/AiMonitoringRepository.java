package com.vegeai.backend.modules.admin.repository;

import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side queries over {@link AiAuditLog} for the admin monitoring dashboard.
 */
public interface AiMonitoringRepository extends JpaRepository<AiAuditLog, UUID>, JpaSpecificationExecutor<AiAuditLog> {

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    @Query("select log.latencyMs from AiAuditLog log where log.createdAt >= :from order by log.latencyMs")
    List<Long> latenciesSince(@Param("from") Instant from);

    @Query("""
            select log.operation as operation, count(log) as totalRequests,
                   sum(case when log.status = 'SUCCESS' then 0 else 1 end) as failedRequests,
                   avg(log.latencyMs) as averageLatencyMs
            from AiAuditLog log where log.createdAt >= :from
            group by log.operation
            """)
    List<OperationSummary> summarizeOperationsSince(@Param("from") Instant from);

    AiAuditLog findFirstByOperationOrderByCreatedAtDesc(String operation);

    AiAuditLog findFirstByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(log.totalTokens), 0) from AiAuditLog log where log.createdAt >= :from")
    long sumTokensSince(@Param("from") Instant from);

    long countByOperationAndStatusAndCreatedAtGreaterThanEqual(String operation, String status, Instant from);

    long countByOperationAndOutcomeAndCreatedAtGreaterThanEqual(String operation, String outcome, Instant from);

    interface OperationSummary {
        String getOperation();
        long getTotalRequests();
        long getFailedRequests();
        Double getAverageLatencyMs();
    }
}
