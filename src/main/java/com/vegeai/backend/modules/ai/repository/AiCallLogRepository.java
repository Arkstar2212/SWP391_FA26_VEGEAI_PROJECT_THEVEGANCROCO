package com.vegeai.backend.modules.ai.repository;

import com.vegeai.backend.modules.ai.entity.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, UUID> {
    long countByCreatedAtAfter(Instant since);
    long countByCreatedAtAfterAndStatus(Instant since, String status);
    List<AiCallLog> findTop50ByOrderByCreatedAtDesc();

    @Query("select avg(log.latencyMs) from AiCallLog log where log.createdAt > :since")
    Double averageLatencySince(@Param("since") Instant since);

    @Query("""
            select log.operation as operation, count(log) as totalRequests,
                   sum(case when log.status = 'SUCCESS' then 0 else 1 end) as failedRequests,
                   avg(log.latencyMs) as averageLatencyMs
            from AiCallLog log where log.createdAt > :since
            group by log.operation order by count(log) desc
            """)
    List<OperationSummary> summarizeOperationsSince(@Param("since") Instant since);

    interface OperationSummary {
        String getOperation();
        long getTotalRequests();
        long getFailedRequests();
        Double getAverageLatencyMs();
    }
}
