package com.vegeai.backend.modules.admin.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.admin.dto.AiMonitoringDtos.*;
import com.vegeai.backend.modules.admin.entity.AdminActionLog;
import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import com.vegeai.backend.modules.admin.entity.AiEventLock;
import com.vegeai.backend.modules.admin.repository.AdminActionLogRepository;
import com.vegeai.backend.modules.admin.repository.AiEventLockRepository;
import com.vegeai.backend.modules.admin.repository.AiMonitoringRepository;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates recorded AI telemetry, administrator actions and server logs for the admin monitoring dashboard.
 * It never probes providers except through the explicit admin replay action.
 */
@Service
@RequiredArgsConstructor
public class AiMonitoringService {

    /** Every operation wrapped by AiAuditService, shown even before its first call. */
    public static final List<String> OPERATIONS = List.of(
            "NUTRITION_CHAT", "FRIDGE_SCAN", "RECIPE_GENERATION", "MEAL_PLAN_RANKING", "CONTENT_MODERATION");
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_EXPORT_ROWS = 5000;
    private static final int MAX_AUDIT_WINDOW = 2000;
    private static final String MODERATION = "CONTENT_MODERATION";
    private static final String AI_EVENT = "AI_EVENT";

    private final AiMonitoringRepository logs;
    private final AiEventLockRepository locks;
    private final AdminActionLogRepository adminActions;
    private final BlogPostRepository blogPosts;
    private final UserRepository users;
    private final SystemLogBuffer systemLogs;
    private final AiReplayService replayService;

    @Transactional(readOnly = true)
    public Overview overview() {
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofHours(24));
        long total = logs.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, now);
        long previous = logs.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from.minus(Duration.ofHours(24)), from);

        Map<String, AiMonitoringRepository.OperationSummary> byOperation = logs.summarizeOperationsSince(from).stream()
                .collect(Collectors.toMap(AiMonitoringRepository.OperationSummary::getOperation, Function.identity()));
        long failed = byOperation.values().stream().mapToLong(AiMonitoringRepository.OperationSummary::getFailedRequests).sum();
        List<Long> latencies = logs.latenciesSince(from);

        long moderationChecks = logs.countByOperationAndStatusAndCreatedAtGreaterThanEqual(MODERATION, "SUCCESS", from);
        long moderationFlagged = logs.countByOperationAndOutcomeAndCreatedAtGreaterThanEqual(MODERATION, "FLAGGED", from);
        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long guestToday = logs.count(between(startOfToday).and(equal("userType", "GUEST")));

        AiAuditLog latest = logs.findFirstByOrderByCreatedAtDesc();
        CoreStatus core = latest == null ? CoreStatus.NO_DATA
                : "SUCCESS".equals(latest.getStatus()) ? CoreStatus.LIVE : CoreStatus.DEGRADED;

        List<String> operations = new ArrayList<>(OPERATIONS);
        byOperation.keySet().stream().filter(operation -> !operations.contains(operation)).sorted().forEach(operations::add);
        List<ServiceStatus> services = operations.stream()
                .map(operation -> serviceStatus(operation, byOperation.get(operation)))
                .toList();

        return new Overview(now, core, total, previous,
                previous == 0 ? null : round((total - previous) * 100.0 / previous),
                percentile(latencies, 0.95),
                latencies.isEmpty() ? null : round(latencies.stream().mapToLong(Long::longValue).average().orElse(0)),
                total == 0 ? null : round((total - failed) * 100.0 / total), failed,
                moderationChecks, moderationFlagged,
                moderationChecks == 0 ? null : round(moderationFlagged * 100.0 / moderationChecks),
                logs.sumTokensSince(from), guestToday,
                blogPosts.count(adminDecisions()) + adminActions.count(),
                systemLogs.snapshot().size(), systemLogs.startedAt(), services);
    }

    @Transactional(readOnly = true)
    public PageResponse<Event> events(TimeRange range, String operation, EventLevel level, String search, int page, int size) {
        Page<AiAuditLog> result = logs.findAll(eventFilter(range, operation, level, search),
                PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, toEvents(result.getContent()));
    }

    @Transactional(readOnly = true)
    public List<Event> exportEvents(TimeRange range, String operation, EventLevel level, String search) {
        return toEvents(logs.findAll(eventFilter(range, operation, level, search),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
    }

    @Transactional
    public LockResult toggleLock(UUID requestId, String reason) {
        AiAuditLog event = requireEvent(requestId);
        boolean locked;
        if (locks.existsById(requestId)) {
            locks.deleteById(requestId);
            locked = false;
        } else {
            AiEventLock lock = new AiEventLock();
            lock.setEventId(requestId);
            lock.setLockedBy(AiCallContext.currentUserId());
            lock.setReason(reason == null || reason.isBlank() ? null : reason.trim());
            locks.save(lock);
            locked = true;
        }
        recordAction(locked ? "AI_EVENT_LOCKED" : "AI_EVENT_UNLOCKED", requestId,
                operationTarget(event) + (locked && reason != null && !reason.isBlank() ? " · " + reason.trim() : ""));
        return new LockResult(requestId, locked);
    }

    /** Not transactional: the provider call can take up to 30 s and must not hold a database transaction. */
    public ReplayResult replay(UUID requestId) {
        AiAuditLog event = requireEvent(requestId);
        ReplayResult result = replayService.probe(requestId, event.getModel());
        recordAction("AI_REPLAY_TEST", requestId, operationTarget(event) + " · " + result.status()
                + (result.errorType() == null ? "" : " · " + result.errorType()) + " · " + result.latencyMs() + " ms");
        return result;
    }

    /** Blog review decisions merged with dashboard actions, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> auditEntries(int page, int size) {
        int pageSize = clampSize(size);
        int safePage = Math.max(page, 0);
        int window = Math.min((safePage + 1) * pageSize, MAX_AUDIT_WINDOW);

        List<BlogPost> decisions = blogPosts.findAll(adminDecisions(),
                PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "reviewedAt"))).getContent();
        List<AdminActionLog> actions = adminActions.findAll(
                PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        Map<UUID, User> admins = usersById(actions.stream().map(AdminActionLog::getAdminId));

        List<AuditEntry> merged = Stream.concat(
                        decisions.stream().map(AiMonitoringService::toAuditEntry),
                        actions.stream().map(action -> toAuditEntry(action, admins.get(action.getAdminId()))))
                .sorted(Comparator.comparing(AuditEntry::timestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        long total = blogPosts.count(adminDecisions()) + adminActions.count();
        int fromIndex = Math.min(safePage * pageSize, merged.size());
        List<AuditEntry> items = merged.subList(fromIndex, Math.min(fromIndex + pageSize, merged.size()));
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        return PageResponse.<AuditEntry>builder().items(items).page(safePage).size(pageSize)
                .totalElements(total).totalPages(totalPages)
                .first(safePage == 0).last(safePage >= totalPages - 1).build();
    }

    public PageResponse<SystemLogEntry> systemLogs(String level, String search, int page, int size) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<SystemLogEntry> filtered = systemLogs.snapshot().stream()
                .filter(entry -> level == null || level.isBlank() || entry.level().equalsIgnoreCase(level))
                .filter(entry -> term.isEmpty() || contains(entry.message(), term) || contains(entry.loggerName(), term)
                        || contains(entry.error(), term))
                .toList();
        int pageSize = clampSize(size);
        int safePage = Math.max(page, 0);
        int fromIndex = Math.min(safePage * pageSize, filtered.size());
        int totalPages = (int) Math.ceil(filtered.size() / (double) pageSize);
        return PageResponse.<SystemLogEntry>builder()
                .items(filtered.subList(fromIndex, Math.min(fromIndex + pageSize, filtered.size())))
                .page(safePage).size(pageSize).totalElements(filtered.size()).totalPages(totalPages)
                .first(safePage == 0).last(safePage >= totalPages - 1).build();
    }

    private ServiceStatus serviceStatus(String operation, AiMonitoringRepository.OperationSummary summary) {
        AiAuditLog latest = logs.findFirstByOperationOrderByCreatedAtDesc(operation);
        if (summary == null || summary.getTotalRequests() == 0) {
            return new ServiceStatus(operation, ServiceHealth.NO_DATA, 0, 0, null, null,
                    latest == null ? null : latest.getStatus(), latest == null ? null : latest.getCreatedAt(),
                    latest == null ? null : latest.getModel());
        }
        double successRate = (summary.getTotalRequests() - summary.getFailedRequests()) * 100.0 / summary.getTotalRequests();
        ServiceHealth health = successRate >= 95 ? ServiceHealth.HEALTHY : successRate >= 80 ? ServiceHealth.WARNING : ServiceHealth.DOWN;
        return new ServiceStatus(operation, health, summary.getTotalRequests(), summary.getFailedRequests(),
                round(successRate), summary.getAverageLatencyMs() == null ? null : round(summary.getAverageLatencyMs()),
                latest.getStatus(), latest.getCreatedAt(), latest.getModel());
    }

    private List<Event> toEvents(List<AiAuditLog> entries) {
        Map<UUID, User> callers = usersById(entries.stream().map(AiAuditLog::getUserId));
        Set<UUID> locked = locks.findAllById(entries.stream().map(AiAuditLog::getId).toList()).stream()
                .map(AiEventLock::getEventId).collect(Collectors.toSet());
        return entries.stream().map(log -> {
            User caller = log.getUserId() == null ? null : callers.get(log.getUserId());
            return new Event(log.getId(), log.getCreatedAt(), log.getOperation(), log.getModel(), log.getUserType(),
                    log.getUserId(), caller == null ? null : displayName(caller), log.getStatus(), log.getOutcome(),
                    log.getErrorType(), log.getLatencyMs(), log.getPromptTokens(), log.getResponseTokens(),
                    log.getTotalTokens(), locked.contains(log.getId()));
        }).toList();
    }

    private Map<UUID, User> usersById(Stream<UUID> ids) {
        Set<UUID> distinct = ids.filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) return Map.of();
        return users.findAllById(distinct).stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
    }

    private AiAuditLog requireEvent(UUID requestId) {
        return logs.findById(requestId).orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Không tìm thấy request AI."));
    }

    private void recordAction(String action, UUID requestId, String detail) {
        AdminActionLog entry = new AdminActionLog();
        entry.setAdminId(AiCallContext.currentUserId());
        entry.setAction(action);
        entry.setTargetType(AI_EVENT);
        entry.setTargetId(requestId.toString());
        entry.setDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
        adminActions.save(entry);
    }

    private static String operationTarget(AiAuditLog event) {
        return event.getOperation() + " · " + event.getModel();
    }

    private static Specification<AiAuditLog> eventFilter(TimeRange range, String operation, EventLevel level, String search) {
        Instant now = Instant.now();
        Instant from = switch (range == null ? TimeRange.LAST_24_HOURS : range) {
            case TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            case LAST_24_HOURS -> now.minus(Duration.ofHours(24));
            case LAST_7_DAYS -> now.minus(Duration.ofDays(7));
            case LAST_30_DAYS -> now.minus(Duration.ofDays(30));
        };
        Specification<AiAuditLog> spec = between(from);
        if (operation != null && !operation.isBlank()) spec = spec.and(equal("operation", operation.trim()));
        if (level == EventLevel.SUCCESS) spec = spec.and(equal("status", "SUCCESS"));
        if (level == EventLevel.FAILED) spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), "SUCCESS"));
        if (level == EventLevel.FLAGGED) spec = spec.and(equal("outcome", "FLAGGED"));
        String term = normalizeSearch(search);
        if (!term.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> matchingUsers = query.subquery(UUID.class);
                Root<User> user = matchingUsers.from(User.class);
                matchingUsers.select(user.get("userId")).where(cb.or(
                        cb.like(cb.lower(user.get("username")), "%" + term + "%"),
                        cb.like(cb.lower(user.get("fullName")), "%" + term + "%")));
                return cb.or(
                        cb.like(cb.lower(root.get("id").as(String.class)), term + "%"),
                        cb.like(cb.lower(root.get("model")), "%" + term + "%"),
                        cb.like(cb.lower(root.get("operation")), "%" + term + "%"),
                        cb.like(cb.lower(root.get("errorType")), "%" + term + "%"),
                        root.get("userId").in(matchingUsers));
            });
        }
        return spec;
    }

    /** Accepts request IDs as displayed in the UI ("#REQ-1A2B3C4D") as well as raw UUID prefixes and names. */
    static String normalizeSearch(String search) {
        if (search == null) return "";
        String term = search.trim().toLowerCase(Locale.ROOT);
        if (term.startsWith("#")) term = term.substring(1);
        if (term.startsWith("req-")) term = term.substring(4);
        return term.replace("%", "").replace("_", "").trim();
    }

    private static Specification<AiAuditLog> between(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private static Specification<AiAuditLog> equal(String field, String value) {
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    /** Admin review decisions; self-published admin posts record the author as reviewer and are excluded. */
    private static Specification<BlogPost> adminDecisions() {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("reviewedAt")),
                cb.isNotNull(root.get("reviewer")),
                cb.notEqual(root.get("reviewer"), root.get("author")));
    }

    private static AuditEntry toAuditEntry(BlogPost post) {
        return new AuditEntry("blog-" + post.getId(), post.getReviewedAt().atZone(ZoneId.systemDefault()).toInstant(),
                displayName(post.getReviewer()), "BLOG_" + post.getStatus(), "BLOG_POST", post.getId().toString(),
                post.getTitle(), post.getAiModerationStatus() == null ? null : post.getAiModerationStatus().name(),
                post.getAiModerationAgreedWithAdmin(), post.getModerationReason());
    }

    private static AuditEntry toAuditEntry(AdminActionLog action, User admin) {
        return new AuditEntry("action-" + action.getId(), action.getCreatedAt(),
                admin == null ? null : displayName(admin), action.getAction(), action.getTargetType(),
                action.getTargetId(), null, null, null, action.getDetail());
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    private static boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    static Double percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return null;
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return (double) sortedValues.get(Math.min(Math.max(index, 0), sortedValues.size() - 1));
    }

    private static Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
