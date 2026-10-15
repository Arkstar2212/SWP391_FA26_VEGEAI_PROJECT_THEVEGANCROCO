package com.vegeai.backend.modules.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.user.dto.ChangePasswordRequest;
import com.vegeai.backend.modules.user.dto.QuickProfileRequest;
import com.vegeai.backend.modules.user.dto.UpdateUserProfileRequest;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

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

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .username("test_vege")
                .email("user@vegeai.com")
                .passwordHash(passwordEncoder.encode("CurrentPass@123"))
                .fullName("Test Vegetarian")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);

        userToken = "Bearer " + jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("GET /api/users/me - Returns authenticated user profile")
    void getMyProfile_success() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("test_vege"))
                .andExpect(jsonPath("$.data.email").value("user@vegeai.com"))
                .andExpect(jsonPath("$.data.fullName").value("Test Vegetarian"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
    }

    @Test
    @DisplayName("GET /api/users/me - Unauthenticated returns 401")
    void getMyProfile_unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("PUT /api/users/me - Updates profile and computes deterministic BMI")
    void updateMyProfile_recalculatesBmi() throws Exception {
        UpdateUserProfileRequest req = UpdateUserProfileRequest.builder()
                .fullName("Vegetarian Green")
                .heightCm(170.0)
                .weightKg(68.0) // BMI = 68 / (1.7^2) = 23.53
                .dietaryPreferences("VEGAN")
                .allergies("PEANUTS")
                .dailyCalorieTarget(2100)
                .build();

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Vegetarian Green"))
                .andExpect(jsonPath("$.data.dietaryPreferences").value("VEGAN"))
                .andExpect(jsonPath("$.data.bmi").value(23.53))
                .andExpect(jsonPath("$.data.dailyCalorieTarget").value(2100));

        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(23.53, updatedUser.getBmi());
        assertEquals("VEGAN", updatedUser.getDietaryPreferences());
    }

    @Test
    @DisplayName("PUT /api/users/me - Fails when user status is PENDING_VERIFICATION")
    void updateMyProfile_failsWhenPendingVerification() throws Exception {
        testUser.setStatus(AccountStatus.PENDING_VERIFICATION);
        userRepository.save(testUser);

        UpdateUserProfileRequest req = UpdateUserProfileRequest.builder()
                .fullName("Unverified Name")
                .build();

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_PENDING_VERIFICATION.getCode()));
    }

    @Test
    @DisplayName("POST /api/users/me/password/request-otp - Endpoint removed returns 404/405")
    void requestPasswordChangeOtp_endpointRemoved() throws Exception {
        mockMvc.perform(post("/api/users/me/password/request-otp")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/users/me/password - Fails with 400 when currentPassword is missing")
    void changePassword_missingCurrentPassword_fails() throws Exception {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .newPassword("BrandNewPass@456")
                .confirmNewPassword("BrandNewPass@456")
                .build();

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("PUT /api/users/me/password - Successfully updates password")
    void changePassword_success() throws Exception {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("CurrentPass@123")
                .newPassword("BrandNewPass@456")
                .confirmNewPassword("BrandNewPass@456")
                .build();

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertTrue(passwordEncoder.matches("BrandNewPass@456", updatedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("PUT /api/users/me/password - Fails when current password is wrong")
    void changePassword_wrongCurrentPassword() throws Exception {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("WrongPassword@123")
                .newPassword("BrandNewPass@456")
                .confirmNewPassword("BrandNewPass@456")
                .build();

        mockMvc.perform(put("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.OLD_PASSWORD_INCORRECT.getCode()));
    }

    @Test
    @DisplayName("DELETE /api/users/me - Deactivates user account")
    void deactivateMyAccount_success() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User updatedUser = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals(AccountStatus.DISABLED, updatedUser.getStatus());
    }

    @Test
    @DisplayName("GET /api/users/{userId}/public - Returns safe public profile without sensitive data")
    void getPublicProfile_success() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getUserId() + "/public")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("test_vege"))
                .andExpect(jsonPath("$.data.fullName").value("Test Vegetarian"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.bmi").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/{userId}/public - Accessible without authentication token (Guest/Public)")
    void getPublicProfile_unauthenticated_success() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getUserId() + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("test_vege"))
                .andExpect(jsonPath("$.data.fullName").value("Test Vegetarian"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.bmi").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/users/me/quick-profile - Quick updates dietary preferences & allergies from modal")
    void updateQuickProfile_success() throws Exception {
        QuickProfileRequest req = QuickProfileRequest.builder()
                .dietaryPreferences("VEGAN")
                .allergies("PEANUTS, SOY")
                .heightCm(175.0)
                .weightKg(68.0)
                .dailyCalorieTarget(2000)
                .build();

        mockMvc.perform(put("/api/users/me/quick-profile")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dietaryPreferences").value("VEGAN"))
                .andExpect(jsonPath("$.data.allergies").value("PEANUTS, SOY"))
                .andExpect(jsonPath("$.data.bmi").value(closeTo(22.2, 0.1)))
                .andExpect(jsonPath("$.data.isProfileCompleted").value(true));

        User updated = userRepository.findById(testUser.getUserId()).orElseThrow();
        assertEquals("VEGAN", updated.getDietaryPreferences());
        assertTrue(updated.isProfileCompleted());
    }

    @Test
    @DisplayName("PUT /api/users/me/quick-profile - Blank dietary preferences returns 400 Bad Request")
    void updateQuickProfile_blankDietary_badRequest() throws Exception {
        QuickProfileRequest req = QuickProfileRequest.builder()
                .dietaryPreferences("")
                .build();

        mockMvc.perform(put("/api/users/me/quick-profile")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
