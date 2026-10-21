package com.vegeai.backend.modules.admin.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.admin.dto.*;
<<<<<<< HEAD
import com.vegeai.backend.modules.ai.entity.AiCallLog;
import com.vegeai.backend.modules.ai.repository.AiCallLogRepository;
=======
import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import com.vegeai.backend.modules.admin.repository.AiAuditLogRepository;
>>>>>>> 6a63815723b213a298a5c7dd70a9dbc7665793ab
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
<<<<<<< HEAD
    private final AiCallLogRepository aiCallLogRepository;

    @Value("${app.gemini.model:gemini-3.8-flash}")
    private String geminiModel;
=======
    private final AiAuditLogRepository aiAuditLogs;
    private final BlogPostRepository blogPosts;

    private String geminiModel = "gemini-3.8-flash";

    public AdminService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                        AiAuditLogRepository aiAuditLogs, BlogPostRepository blogPosts) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.aiAuditLogs = aiAuditLogs;
        this.blogPosts = blogPosts;
    }

    @Value("${app.gemini.model:gemini-3.8-flash}")
    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }
>>>>>>> 6a63815723b213a298a5c7dd70a9dbc7665793ab

    /**
     * Search and list users with dynamic multi-criteria filtering and pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listUsers(
            int page, int size, String search, UserRole role, AccountStatus status,
            String sortBy, String direction) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortProperty = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(dir, sortProperty));

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), pattern);
                Predicate emailMatch    = cb.like(cb.lower(root.get("email")), pattern);
                Predicate nameMatch     = cb.like(cb.lower(root.get("fullName")), pattern);
                predicates.add(cb.or(usernameMatch, emailMatch, nameMatch));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> userPage = userRepository.findAll(spec, pageRequest);
        List<UserProfileResponse> dtos = userPage.getContent().stream()
                .map(UserProfileResponse::forAdministration)
                .toList();

        return PageResponse.from(userPage, dtos);
    }

    /**
     * View detailed user account.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.forAdministration(user);
    }

    /**
     * Update account status (Lock, Unlock, Activate, Disable).
     * Enforces the Admin Immunity Rule: Admins cannot lock or disable other admins or themselves.
     */
    @Transactional
    public UserProfileResponse updateUserStatus(UUID targetUserId, UpdateUserStatusRequest req, String adminCallerIdStr) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UUID adminCallerId = parseUuid(adminCallerIdStr);

        // 1. Cannot modify self
        if (targetUser.getUserId().equals(adminCallerId)) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_SELF_STATUS,
                    "Administrators cannot modify their own account status.");
        }

        // 2. Admin Immunity Rule: Target cannot be another ADMIN
        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new AppException(ErrorCode.ADMIN_IMMUNITY_VIOLATION,
                    "Admin immunity policy: Cannot modify another administrator account.");
        }

        targetUser.setStatus(req.getStatus());
        User saved = userRepository.save(targetUser);

        // Revoke active sessions if account is locked or disabled
        if (req.getStatus() == AccountStatus.LOCKED || req.getStatus() == AccountStatus.DISABLED) {
            refreshTokenRepository.revokeAllByUser(targetUser);
        }

        log.info("[ADMIN] Status updated for user: {} to {} by admin: {}. Reason: {}",
                targetUser.getUsername(), req.getStatus(), adminCallerIdStr, req.getReason());

        return UserProfileResponse.forAdministration(saved);
    }

    /**
     * Change user role (Promote to ADMIN or Demote to MEMBER).
     * Protects self-demotion.
     */
    @Transactional
    public UserProfileResponse updateUserRole(UUID targetUserId, UpdateUserRoleRequest req, String adminCallerIdStr) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UUID adminCallerId = parseUuid(adminCallerIdStr);

        if (targetUser.getUserId().equals(adminCallerId)) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_SELF_STATUS,
                    "Administrators cannot change their own role.");
        }

        targetUser.setRole(req.getRole());
        User saved = userRepository.save(targetUser);

        log.info("[ADMIN] Role updated for user: {} to {} by admin: {}",
                targetUser.getUsername(), req.getRole(), adminCallerIdStr);

        return UserProfileResponse.forAdministration(saved);
    }

    /**
     * Aggregate platform statistics for Super Admin dashboard.
     */
    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);

        return AdminDashboardStatsResponse.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByStatus(AccountStatus.ACTIVE))
                .pendingUsers(userRepository.countByStatus(AccountStatus.PENDING_VERIFICATION))
                .lockedUsers(userRepository.countByStatus(AccountStatus.LOCKED))
                .disabledUsers(userRepository.countByStatus(AccountStatus.DISABLED))
                .memberCount(userRepository.countByRole(UserRole.MEMBER))
                .adminCount(userRepository.countByRole(UserRole.ADMIN))
                .newUsersToday(userRepository.countByCreatedAtAfter(today.atStartOfDay()))
                .newUsersThisWeek(userRepository.countByCreatedAtAfter(weekAgo.atStartOfDay()))
                .build();
    }

<<<<<<< HEAD
    @Transactional(readOnly = true)
    public AiOpsStatusResponse getAiOpsStatus() {
        Instant now = Instant.now();
        Instant since = now.minusSeconds(24 * 60 * 60);
        long total = aiCallLogRepository.countByCreatedAtAfter(since);
        long successful = aiCallLogRepository.countByCreatedAtAfterAndStatus(since, "SUCCESS");
        List<AiCallLog> recent = aiCallLogRepository.findTop50ByOrderByCreatedAtDesc();
        AiCallLog latest = recent.isEmpty() || !recent.get(0).getCreatedAt().isAfter(since) ? null : recent.get(0);
        String health = latest == null ? "NO_DATA" : "SUCCESS".equals(latest.getStatus()) ? "HEALTHY" : "DEGRADED";
        return AiOpsStatusResponse.builder()
                .systemHealth(health)
                .successRatePercentage(total == 0 ? null : Math.round(successful * 1000.0 / total) / 10.0)
                .averageLatencyMs(total == 0 ? null : aiCallLogRepository.averageLatencySince(since))
                .totalAiRequests(total)
                .failedAiRequests(total - successful)
                .generatedAt(now)
                .models(List.of(AiModelStatus.builder()
                        .modelName(geminiModel)
                        .provider("Google Gemini")
                        .version(geminiModel)
                        .status(latest == null ? "NO_DATA" : latest.getStatus())
                        .latencyMs(latest == null ? null : latest.getLatencyMs())
                        .build()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<AiAuditLogEntry> getAiAuditLogs() {
        return aiCallLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(log -> AiAuditLogEntry.builder()
                        .logId(log.getId().toString())
                        .timestamp(log.getCreatedAt())
                        .model(log.getModel())
                        .operation(log.getOperation())
                        .latencyMs(log.getLatencyMs())
                        .status(log.getStatus())
                        .errorType(log.getErrorType())
                        .build())
                .toList();
=======
    /**
     * Summarizes recorded AI request telemetry; this does not actively probe providers.
     */
    @Transactional(readOnly = true)
    public AiOpsStatusResponse getAiOpsStatus() {
        Instant from = Instant.now().minus(Duration.ofHours(24));
        long requests = aiAuditLogs.countByCreatedAtAfter(from);
        long successes = aiAuditLogs.countByCreatedAtAfterAndStatus(from, "SUCCESS");
        Instant today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long guestRequests = aiAuditLogs.countByCreatedAtAfterAndUserType(today, "GUEST");
        List<AiModerationStatus> reviewedStatuses = List.of(AiModerationStatus.SAFE, AiModerationStatus.FLAGGED);
        long reviewed = blogPosts.countAiModerationReviewed(reviewedStatuses);
        long agreements = blogPosts.countAiModerationAgreements(reviewedStatuses);
        AiAuditLog latest = aiAuditLogs.findFirstByModelAndCreatedAtAfterOrderByCreatedAtDesc(geminiModel, from);
        double successRate = requests == 0 ? 0 : successes * 100.0 / requests;
        String health = requests == 0 ? "NO_DATA" : successRate < 95 ? "DEGRADED" : "HEALTHY";
        int modelLatency = (int) Math.round(aiAuditLogs.averageLatencySinceForModel(from, geminiModel));
        String modelStatus = latest == null ? "NO_DATA" : "SUCCESS".equals(latest.getStatus()) ? "OPERATIONAL" : "DEGRADED";
        List<AiModelStatus> models = List.of(AiModelStatus.builder()
                .modelName("Gemini")
                .provider("Google AI Studio")
                .version(geminiModel)
                .status(modelStatus)
                .latencyMs(modelLatency)
                .rateLimitQuota("Theo giới hạn của nhà cung cấp")
                .build());

        return AiOpsStatusResponse.builder()
                .systemHealth(health)
                .successRatePercentage(requests == 0 ? null : Math.round(successRate * 100.0) / 100.0)
                .averageLatencyMs(requests == 0 ? null : aiAuditLogs.averageLatencySince(from))
                .totalAiRequests(requests)
                .guestAiRequestsToday(guestRequests)
                .moderationReviewedCount(reviewed)
                .moderationAgreementRate(reviewed == 0 ? null : Math.round(agreements * 10000.0 / reviewed) / 100.0)
                .generatedAt(Instant.now())
                .models(models)
                .build();
    }

    /**
     * Recent AI audit trail logs for administrative review.
     */
    @Transactional(readOnly = true)
    public List<AiAuditLogEntry> getAiAuditLogs() {
        return aiAuditLogs.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toAuditEntry)
                .toList();
    }

    private AiAuditLogEntry toAuditEntry(AiAuditLog logEntry) {
        return AiAuditLogEntry.builder()
                .logId(logEntry.getId().toString())
                .timestamp(logEntry.getCreatedAt())
                .model(logEntry.getModel())
                .operation(logEntry.getOperation())
                .latencyMs(logEntry.getLatencyMs())
                .userType(logEntry.getUserType())
                .status(logEntry.getStatus())
                .outcome(logEntry.getOutcome())
                .errorType(logEntry.getErrorType())
                .build();
>>>>>>> 6a63815723b213a298a5c7dd70a9dbc7665793ab
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid user ID format.");
        }
    }
}
