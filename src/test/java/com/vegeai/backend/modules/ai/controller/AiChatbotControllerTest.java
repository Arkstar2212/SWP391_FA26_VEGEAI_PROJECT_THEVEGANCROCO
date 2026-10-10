package com.vegeai.backend.modules.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.ai.dto.AiChatRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = User.builder()
                .username("aicheckuser")
                .email("aiuser@vegeai.com")
                .passwordHash(passwordEncoder.encode("hashed_pass"))
                .fullName("Nguyen Van Test")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences("VEGAN")
                .allergies("PEANUTS")
                .heightCm(175.0)
                .weightKg(68.0)
                .bmi(22.2)
                .dailyCalorieTarget(2000)
                .build();
        testUser = userRepository.save(testUser);

        userToken = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Guest chat: should return AI advisory response with trial flag")
    void chatAsGuest_success() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Tư vấn cho tôi các món chay giàu protein")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("X-Forwarded-For", "10.150.1." + (System.currentTimeMillis() % 250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendedRecipes").isArray())
                .andExpect(jsonPath("$.data.recommendedRecipes", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.isGuestTrial").value(true));
    }

    @Test
    @DisplayName("Authenticated chat: should succeed and personalize response")
    void chatAsMember_success() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Tôi muốn lên thực đơn giảm cân với rau xanh")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendedRecipes").isArray())
                .andExpect(jsonPath("$.data.isGuestTrial").value(false));
    }

    @Test
    @DisplayName("Chat with empty message: should fail with validation error")
    void chatEmptyMessage_badRequest() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("X-Forwarded-For", "10.150.2." + (System.currentTimeMillis() % 250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
