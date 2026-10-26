package com.vegeai.backend.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import com.vegeai.backend.modules.admin.repository.AdminActionLogRepository;
import com.vegeai.backend.modules.admin.repository.AiAuditLogRepository;
import com.vegeai.backend.modules.admin.repository.AiEventLockRepository;
import com.vegeai.backend.modules.admin.service.AiAuditLogWriter;
import com.vegeai.backend.modules.admin.service.AiCallContext;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.community.entity.AiModerationStatus;
import com.vegeai.backend.modules.community.entity.BlogCategory;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiMonitoringControllerTest {

    private static final String BASE = "/api/admin/ai/monitoring";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiAuditLogRepository aiAuditLogRepository;
    @Autowired private AiAuditLogWriter aiAuditLogWriter;
    @Autowired private AiEventLockRepository aiEventLockRepository;
    @Autowired private AdminActionLogRepository adminActionLogRepository;
    @Autowired private BlogPostRepository blogPostRepository; @Autowired(required = false) private com.vegeai.backend.modules.community.repository.BlogCommentRepository blogCommentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private User admin;
    private User member;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        cleanTables();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = userRepository.save(user("monitor_admin_" + suffix, UserRole.ADMIN, "Quản Trị Viên"));
        member = userRepository.save(user("monitor_member_" + suffix, UserRole.MEMBER, "Nguyễn Hoàng Minh"));
        adminToken = "Bearer " + jwtService.generateAccessToken(admin);
        memberToken = "Bearer " + jwtService.generateAccessToken(member);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanTables();
        userRepository.deleteAll(List.of(admin, member));
    }

    private void cleanTables() { if (blogCommentRepository != null) blogCommentRepository.deleteAll();
        blogPostRepository.deleteAll();
        aiAuditLogRepository.deleteAll();
        aiEventLockRepository.deleteAll();
        adminActionLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Overview aggregates 24h KPIs, tokens, core status and every AI service")
    void overviewAggregatesRecordedTelemetry() throws Exception {
        saveLog("NUTRITION_CHAT", "SUCCESS", null, "GUEST", 100, null, null, 120);
        saveLog("NUTRITION_CHAT", "SUCCESS", null, "MEMBER", 200, null, member.getUserId(), 80);
        saveLog("FRIDGE_SCAN", "FAILED", null, "MEMBER", 900, "HTTP_429", member.getUserId(), null);
        saveLog("CONTENT_MODERATION", "SUCCESS", "FLAGGED", "MEMBER", 300, null, null, null);
        saveLog("CONTENT_MODERATION", "SUCCESS", "SAFE", "MEMBER", 400, null, null, null);

        mockMvc.perform(get(BASE + "/overview").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coreStatus").value("LIVE"))
                .andExpect(jsonPath("$.data.totalRequests").value(5))
                .andExpect(jsonPath("$.data.failedRequests").value(1))
                .andExpect(jsonPath("$.data.successRatePercentage").value(80.0))
                .andExpect(jsonPath("$.data.p95LatencyMs").value(900.0))
                .andExpect(jsonPath("$.data.averageLatencyMs").value(380.0))
                .andExpect(jsonPath("$.data.moderationChecks").value(2))
                .andExpect(jsonPath("$.data.moderationFlagged").value(1))
                .andExpect(jsonPath("$.data.flaggedRatePercentage").value(50.0))
                .andExpect(jsonPath("$.data.totalTokens").value(200))
                .andExpect(jsonPath("$.data.guestRequestsToday").value(1))
                .andExpect(jsonPath("$.data.systemLogCount").isNumber())
                .andExpect(jsonPath("$.data.services", hasSize(6)))
                .andExpect(jsonPath("$.data.services[?(@.operation == 'NUTRITION_CHAT')].health", contains("HEALTHY")))
                .andExpect(jsonPath("$.data.services[?(@.operation == 'FRIDGE_SCAN')].health", contains("DOWN")))
                .andExpect(jsonPath("$.data.services[?(@.operation == 'MEAL_PLAN_RANKING')].health", contains("NO_DATA")));
    }

    @Test
    @DisplayName("Events resolve caller names, support filters, request-ID and name search, and paging")
    void eventsAreFilterableAndPaged() throws Exception {
        saveLog("NUTRITION_CHAT", "SUCCESS", null, "GUEST", 100, null, null, 50);
        saveLog("FRIDGE_SCAN", "FAILED", null, "MEMBER", 900, "HTTP_429", member.getUserId(), null);
        AiAuditLog flagged = saveLog("CONTENT_MODERATION", "SUCCESS", "FLAGGED", "MEMBER", 300, null, null, null);

        mockMvc.perform(get(BASE + "/events").param("size", "2").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].promptSummary").doesNotExist());
        mockMvc.perform(get(BASE + "/events").param("level", "FAILED").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].errorType").value("HTTP_429"))
                .andExpect(jsonPath("$.data.items[0].userName").value("Nguyễn Hoàng Minh"));
        mockMvc.perform(get(BASE + "/events").param("level", "FLAGGED").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].outcome").value("FLAGGED"));
        mockMvc.perform(get(BASE + "/events").param("level", "SUCCESS").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].operation").value("NUTRITION_CHAT"));
        String displayedId = "#REQ-" + flagged.getId().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(get(BASE + "/events").param("search", displayedId).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].requestId").value(flagged.getId().toString()));
        mockMvc.perform(get(BASE + "/events").param("search", "hoàng minh").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].operation").value("FRIDGE_SCAN"));
        mockMvc.perform(get(BASE + "/events/export").param("operation", "NUTRITION_CHAT").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userType").value("GUEST"))
                .andExpect(jsonPath("$.data[0].totalTokens").value(50));
    }

    @Test
    @DisplayName("Audit writer stores the signed-in caller and Gemini token usage")
    void auditWriterRecordsCallerAndTokens() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(member.getUserId().toString(), null, List.of()));
        AiCallContext.recordUsage(objectMapper.readTree(
                "{\"promptTokenCount\":12,\"candidatesTokenCount\":30,\"totalTokenCount\":42}"));

        aiAuditLogWriter.save("NUTRITION_CHAT", "gemini-test", "MEMBER", "SUCCESS", null, 250, null);

        AiAuditLog saved = aiAuditLogRepository.findTop50ByOrderByCreatedAtDesc().get(0);
        assertThat(saved.getUserId()).isEqualTo(member.getUserId());
        assertThat(saved.getPromptTokens()).isEqualTo(12);
        assertThat(saved.getResponseTokens()).isEqualTo(30);
        assertThat(saved.getTotalTokens()).isEqualTo(42);

        SecurityContextHolder.clearContext();
        aiAuditLogWriter.save("NUTRITION_CHAT", "gemini-test", "GUEST", "SUCCESS", null, 90, null);
        AiAuditLog guest = aiAuditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(log -> "GUEST".equals(log.getUserType())).findFirst().orElseThrow();
        assertThat(guest.getUserId()).isNull();
        assertThat(guest.getTotalTokens()).as("usage is cleared after being applied").isNull();
    }

    @Test
    @DisplayName("Lock toggles and replay are recorded in the audit trail alongside blog review decisions")
    void lockReplayAndAuditTrail() throws Exception {
        AiAuditLog event = saveLog("NUTRITION_CHAT", "SUCCESS", null, "MEMBER", 100, null, member.getUserId(), 20);
        BlogPost reviewed = blogPost(member, admin, BlogStatus.REJECTED, AiModerationStatus.FLAGGED, true);
        blogPost(admin, admin, BlogStatus.PUBLISHED, AiModerationStatus.NOT_CHECKED, null);

        mockMvc.perform(post(BASE + "/events/" + event.getId() + "/lock").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Kiểm tra lạm dụng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true));
        mockMvc.perform(get(BASE + "/events").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.items[0].locked").value(true));
        mockMvc.perform(post(BASE + "/events/" + event.getId() + "/lock").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.locked").value(false));

        mockMvc.perform(post(BASE + "/events/" + event.getId() + "/replay").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorType").value("AI_NOT_CONFIGURED"));

        mockMvc.perform(get(BASE + "/audit").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[*].action",
                        containsInAnyOrder("AI_EVENT_LOCKED", "AI_EVENT_UNLOCKED", "AI_REPLAY_TEST", "BLOG_REJECTED")))
                .andExpect(jsonPath("$.data.items[?(@.action == 'BLOG_REJECTED')].targetId", contains(reviewed.getId().toString())))
                .andExpect(jsonPath("$.data.items[?(@.action == 'BLOG_REJECTED')].aiAgreedWithAdmin", contains(true)))
                .andExpect(jsonPath("$.data.items[?(@.action == 'AI_EVENT_LOCKED')].adminName", contains("Quản Trị Viên")));
        mockMvc.perform(get(BASE + "/overview").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.data.auditEntryCount").value(4));
    }

    @Test
    @DisplayName("System logs expose recent server log lines with level filtering")
    void systemLogsAreReadable() throws Exception {
        org.slf4j.LoggerFactory.getLogger("com.vegeai.backend.MonitoringProbe").warn("monitoring-probe-warning");

        mockMvc.perform(get(BASE + "/system-logs").param("level", "WARN").param("search", "monitoring-probe")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].message").value("monitoring-probe-warning"))
                .andExpect(jsonPath("$.data.items[0].logger").value("MonitoringProbe"));
    }

    @Test
    @DisplayName("Monitoring endpoints are admin-only")
    void monitoringIsAdminOnly() throws Exception {
        for (String path : new String[]{"/overview", "/events", "/events/export", "/audit", "/system-logs"}) {
            mockMvc.perform(get(BASE + path).header(HttpHeaders.AUTHORIZATION, memberToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
        }
        String eventPath = BASE + "/events/" + UUID.randomUUID();
        mockMvc.perform(post(eventPath + "/lock").header(HttpHeaders.AUTHORIZATION, memberToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(eventPath + "/replay").header(HttpHeaders.AUTHORIZATION, memberToken)).andExpect(status().isForbidden());
    }

    private AiAuditLog saveLog(String operation, String status, String outcome, String userType, long latencyMs,
                               String errorType, UUID userId, Integer totalTokens) {
        AiAuditLog entry = new AiAuditLog();
        entry.setModel("gemini-test");
        entry.setOperation(operation);
        entry.setStatus(status);
        entry.setOutcome(outcome);
        entry.setUserType(userType);
        entry.setLatencyMs(latencyMs);
        entry.setErrorType(errorType);
        entry.setUserId(userId);
        entry.setTotalTokens(totalTokens);
        return aiAuditLogRepository.saveAndFlush(entry);
    }

    private BlogPost blogPost(User author, User reviewer, BlogStatus status, AiModerationStatus aiStatus, Boolean agreed) {
        BlogPost post = new BlogPost();
        post.setAuthor(author);
        post.setTitle("Bài viết " + status);
        post.setContent("Nội dung");
        post.setCategory(BlogCategory.KITCHEN);
        post.setStatus(status);
        post.setAiModerationStatus(aiStatus);
        post.setAiModerationAgreedWithAdmin(agreed);
        post.setReviewer(reviewer);
        post.setReviewedAt(LocalDateTime.now());
        return blogPostRepository.saveAndFlush(post);
    }

    private static User user(String username, UserRole role, String fullName) {
        return User.builder()
                .username(username)
                .email(username + "@vegeai.test")
                .passwordHash("not-used")
                .fullName(fullName)
                .role(role)
                .status(AccountStatus.ACTIVE)
                .build();
    }
}

