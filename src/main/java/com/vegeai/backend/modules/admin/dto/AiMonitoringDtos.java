package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin AI monitoring payloads. Built from recorded telemetry metadata and the caller's account:
 * prompts, images and model responses are never stored or returned.
 */
public final class AiMonitoringDtos {
    private AiMonitoringDtos() {}

    public enum TimeRange { TODAY, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS }

    /** SUCCESS and FAILED match the stored status; FLAGGED is a successful moderation call that flagged content. */
    public enum EventLevel { SUCCESS, FAILED, FLAGGED }

    /** HEALTHY: >= 95% success, WARNING: >= 80%, DOWN: below 80%, NO_DATA: no calls in the window. */
    public enum ServiceHealth { HEALTHY, WARNING, DOWN, NO_DATA }

    /** LIVE when the most recent AI call succeeded, DEGRADED when it failed. */
    public enum CoreStatus { LIVE, DEGRADED, NO_DATA }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Overview(Instant generatedAt, CoreStatus coreStatus,
                           long totalRequests, long previousTotalRequests, Double requestTrendPercentage,
                           Double p95LatencyMs, Double averageLatencyMs,
                           Double successRatePercentage, long failedRequests,
                           long moderationChecks, long moderationFlagged, Double flaggedRatePercentage,
                           long totalTokens, long guestRequestsToday,
                           long auditEntryCount, long systemLogCount, Instant systemLogSince,
                           List<ServiceStatus> services) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServiceStatus(String operation, ServiceHealth health, long totalRequests, long failedRequests,
                                Double successRatePercentage, Double averageLatencyMs,
                                String lastStatus, Instant lastCalledAt, String model) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(UUID requestId, Instant timestamp, String operation, String model, String userType,
                        UUID userId, String userName, String status, String outcome, String errorType,
                        long latencyMs, Integer promptTokens, Integer responseTokens, Integer totalTokens,
                        boolean locked) {}

    /** One administrator action: a blog review decision or an action taken from this dashboard. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuditEntry(String id, Instant timestamp, String adminName, String action, String targetType,
                             String targetId, String targetLabel, String aiVerdict, Boolean aiAgreedWithAdmin,
                             String detail) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SystemLogEntry(long id, Instant timestamp, String level, String logger, String loggerName,
                                 String thread, String message, String error) {}

    public record LockRequest(@Size(max = 500) String reason) {}

    public record LockResult(UUID requestId, boolean locked) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplayResult(UUID sourceRequestId, String status, long latencyMs, Integer totalTokens,
                               String errorType, String message) {}
}
