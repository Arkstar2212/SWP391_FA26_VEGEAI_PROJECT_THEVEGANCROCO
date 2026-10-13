package com.vegeai.backend.modules.nutrition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.nutrition.dto.NutritionCalculateRequest;
import com.vegeai.backend.modules.nutrition.dto.NutritionProfileRequest;
import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import com.vegeai.backend.modules.nutrition.repository.NutritionProfileRepository;
import com.vegeai.backend.modules.user.dto.UpdateUserProfileRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private NutritionProfileRepository profileRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.builder()
                .username("nutri_user")
                .email("nutri@vegeai.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .fullName("Nutrition Tester")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build());
        token = "Bearer " + jwtService.generateAccessToken(user);
    }

    @AfterEach
    void tearDown() {
        // Other test classes call userRepository.deleteAll(); leave no FK rows behind.
        profileRepository.deleteAll();
    }

    private NutritionProfileRequest maleMaintain() {
        return NutritionProfileRequest.builder()
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.now().minusYears(30).minusDays(1))
                .heightCm(170.0)
                .weightKg(68.0)
                .activityLevel(ActivityLevel.MODERATE)
                .goal(NutritionGoal.MAINTAIN)
                .build();
    }

    @Test
    @DisplayName("GET /api/nutrition/profile - 404 before setup")
    void getProfile_notFound() throws Exception {
        mockMvc.perform(get("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NUTRITION_PROFILE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("GET /api/nutrition/profile - 401 without token")
    void getProfile_unauthorized() throws Exception {
        mockMvc.perform(get("/api/nutrition/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/nutrition/profile - creates profile, computes targets and syncs users table")
    void upsert_createsAndSyncs() throws Exception {
        mockMvc.perform(put("/api/nutrition/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maleMaintain())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(30))
                .andExpect(jsonPath("$.data.targets.bmi").value(23.53))
                .andExpect(jsonPath("$.data.targets.bmiCategory").value("OVERWEIGHT"))
                .andExpect(jsonPath("$.data.targets.bmr").value(1598))
                .andExpect(jsonPath("$.data.targets.tdee").value(2477))
                .andExpect(jsonPath("$.data.targets.dailyCaloriesTarget").value(2477))
                .andExpect(jsonPath("$.data.targets.proteinTargetG").value(68));

        assertTrue(profileRepository.existsByUser_UserId(user.getUserId()));
        User synced = userRepository.findById(user.getUserId()).orElseThrow();
        assertEquals(2477, synced.getDailyCalorieTarget());
        assertEquals(23.53, synced.getBmi());
        assertEquals("MALE", synced.getGender());
    }

    @Test
    @DisplayName("PUT twice updates the same profile (upsert)")
    void upsert_updatesExisting() throws Exception {
        String body = objectMapper.writeValueAsString(maleMaintain());
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        NutritionProfileRequest lose = maleMaintain();
        lose.setGoal(NutritionGoal.LOSE_WEIGHT);
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(lose)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targets.dailyCaloriesTarget").value(1977));

        assertEquals(1, profileRepository.count());
    }

    @Test
    @DisplayName("PUT /api/nutrition/profile - validation errors return 400")
    void upsert_validation() throws Exception {
        NutritionProfileRequest bad = maleMaintain();
        bad.setHeightCm(10.0);
        bad.setGoal(null);
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("PUT /api/nutrition/profile - PENDING_VERIFICATION user is blocked (403)")
    void upsert_unverifiedBlocked() throws Exception {
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        userRepository.save(user);

        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(maleMaintain())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_PENDING_VERIFICATION.getCode()));
    }

    @Test
    @DisplayName("PUT /api/nutrition/profile - age under 13 is rejected")
    void upsert_tooYoung() throws Exception {
        NutritionProfileRequest kid = maleMaintain();
        kid.setDateOfBirth(LocalDate.now().minusYears(10));
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(kid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.NUTRITION_AGE_OUT_OF_RANGE.getCode()));
    }

    @Test
    @DisplayName("PUT /api/users/me with new weight recalculates the nutrition profile")
    void userUpdate_resyncsProfile() throws Exception {
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maleMaintain()))).andExpect(status().isOk());

        UpdateUserProfileRequest req = UpdateUserProfileRequest.builder().weightKg(78.0).build();
        mockMvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bmi").value(26.99))
                // BMR = 780 + 1062.5 - 150 + 5 = 1697.5 -> 1698; TDEE = 1698 * 1.55 = 2631.9
                .andExpect(jsonPath("$.data.dailyCalorieTarget").value(2632));

        mockMvc.perform(get("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weightKg").value(78.0))
                .andExpect(jsonPath("$.data.targets.bmiCategory").value("OBESE"));
    }

    @Test
    @DisplayName("DELETE /api/nutrition/profile removes the profile")
    void deleteProfile() throws Exception {
        mockMvc.perform(put("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maleMaintain()))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/nutrition/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        assertFalse(profileRepository.existsByUser_UserId(user.getUserId()));
    }

    @Test
    @DisplayName("POST /api/nutrition/calculate - public, nothing saved")
    void calculate_public() throws Exception {
        NutritionCalculateRequest req = NutritionCalculateRequest.builder()
                .gender(Gender.FEMALE).age(25).heightCm(155.0).weightKg(45.0)
                .activityLevel(ActivityLevel.SEDENTARY).goal(NutritionGoal.LOSE_WEIGHT).build();
        mockMvc.perform(post("/api/nutrition/calculate")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyCaloriesTarget").value(1200))
                .andExpect(jsonPath("$.data.warnings.length()").value(1));
        assertEquals(0, profileRepository.count());
    }

    @Test
    @DisplayName("GET /api/nutrition/options - public enum list")
    void options_public() throws Exception {
        mockMvc.perform(get("/api/nutrition/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityLevels.length()").value(5))
                .andExpect(jsonPath("$.data.goals.length()").value(3));
    }
}
