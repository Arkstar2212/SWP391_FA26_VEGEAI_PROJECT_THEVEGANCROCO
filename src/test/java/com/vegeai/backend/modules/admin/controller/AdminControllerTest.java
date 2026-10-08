package com.vegeai.backend.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.admin.dto.UpdateUserRoleRequest;
import com.vegeai.backend.modules.admin.dto.UpdateUserStatusRequest;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User secondAdmin;
    private User memberUser;

    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = User.builder()
                .username("superadmin")
                .email("admin@vegeai.com")
                .passwordHash(passwordEncoder.encode("AdminPass@123"))
                .fullName("Super Admin")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);

        secondAdmin = User.builder()
                .username("admin_two")
                .email("admin2@vegeai.com")
                .passwordHash(passwordEncoder.encode("AdminPass@123"))
                .fullName("Second Admin")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        secondAdmin = userRepository.save(secondAdmin);

        memberUser = User.builder()
                .username("vegan_dan")
                .email("dan@vegeai.com")
                .passwordHash(passwordEncoder.encode("DanPass@123"))
                .fullName("Dan Vegan")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
        memberUser = userRepository.save(memberUser);

        adminToken = "Bearer " + jwtService.generateAccessToken(adminUser);
        memberToken = "Bearer " + jwtService.generateAccessToken(memberUser);
    }

    @Test
    @DisplayName("GET /api/admin/users - Admin can view users list")
    void listUsers_asAdmin_success() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/admin/users - Member receives 403 Forbidden")
    void listUsers_asMember_forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("GET /api/admin/users - Search by keyword")
    void listUsers_searchFilter() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .param("search", "dan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("vegan_dan"));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{userId}/status - Locks a member account")
    void updateUserStatus_lockMember_success() throws Exception {
        UpdateUserStatusRequest req = UpdateUserStatusRequest.builder()
                .status(AccountStatus.LOCKED)
                .reason("Spam activity")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + memberUser.getUserId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        User updated = userRepository.findById(memberUser.getUserId()).orElseThrow();
        assertEquals(AccountStatus.LOCKED, updated.getStatus());
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{userId}/status - Admin Immunity Rule blocks locking another admin")
    void updateUserStatus_adminImmunity_blocked() throws Exception {
        UpdateUserStatusRequest req = UpdateUserStatusRequest.builder()
                .status(AccountStatus.LOCKED)
                .reason("Disagreement")
                .build();

        mockMvc.perform(patch("/api/admin/users/" + secondAdmin.getUserId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADMIN_IMMUNITY_VIOLATION.getCode()));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{userId}/status - Cannot modify own status")
    void updateUserStatus_modifySelf_blocked() throws Exception {
        UpdateUserStatusRequest req = UpdateUserStatusRequest.builder()
                .status(AccountStatus.LOCKED)
                .build();

        mockMvc.perform(patch("/api/admin/users/" + adminUser.getUserId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CANNOT_MODIFY_SELF_STATUS.getCode()));
    }

    @Test
    @DisplayName("PATCH /api/admin/users/{userId}/role - Promotes member to ADMIN")
    void updateUserRole_promoteToAdmin_success() throws Exception {
        UpdateUserRoleRequest req = UpdateUserRoleRequest.builder()
                .role(UserRole.ADMIN)
                .build();

        mockMvc.perform(patch("/api/admin/users/" + memberUser.getUserId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        User updated = userRepository.findById(memberUser.getUserId()).orElseThrow();
        assertEquals(UserRole.ADMIN, updated.getRole());
    }

    @Test
    @DisplayName("GET /api/admin/dashboard/stats - Returns platform telemetry")
    void getDashboardStats_success() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(3))
                .andExpect(jsonPath("$.data.adminCount").value(2))
                .andExpect(jsonPath("$.data.memberCount").value(1));
    }

    @Test
    @DisplayName("GET /api/admin/ai/status - Returns AI Ops and model status")
    void getAiOpsStatus_success() throws Exception {
        mockMvc.perform(get("/api/admin/ai/status")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemHealth").value("HEALTHY"))
                .andExpect(jsonPath("$.data.models", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/admin/ai/audit-logs - Returns AI audit logs")
    void getAiAuditLogs_success() throws Exception {
        mockMvc.perform(get("/api/admin/ai/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }
}
