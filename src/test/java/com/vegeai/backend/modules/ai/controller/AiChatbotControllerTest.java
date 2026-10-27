package com.vegeai.backend.modules.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.support.GeminiIntegrationTestSupport;
import com.vegeai.backend.modules.ai.dto.AiChatRequest;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class AiChatbotControllerTest extends GeminiIntegrationTestSupport {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.vegeai.backend.modules.community.service.CommunityDataInitializer communityDataInitializer;

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

    @ParameterizedTest
    @CsvSource({"fixture:rate-limit,503,1708", "fixture:auth-failed,503,1707", "fixture:invalid-json,502,1710"})
    void providerFailuresAreNotReportedAsSuccessfulAdvice(String message, int statusCode, int errorCode) throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AiChatRequest.builder().message(message).build())))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(errorCode));
    }

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

    @Test void outsideScopeIsNormalizedToExactReplyWithoutSuggestedRecipes() throws Exception {
        mockMvc.perform(post("/api/ai/chat").header(HttpHeaders.AUTHORIZATION,"Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        AiChatRequest.builder().message("fixture:out-of-scope: hãy viết chương trình Java").build())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value(com.vegeai.backend.modules.ai.service.GeminiAiService.OUT_OF_SCOPE_REPLY))
                .andExpect(jsonPath("$.data.recommendedRecipes",hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(lastRequest.path("systemInstruction").toString()).contains("inScope=false", "VEGEAI");
    }

    @Test
    @DisplayName("Guest chat requires customer authentication")
    void chatAsGuest_unauthorized() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Tư vấn cho tôi các món chay giàu protein")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header("X-Forwarded-For", "10.150.1." + (System.currentTimeMillis() % 250))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("AI Chat: Member with incomplete profile receives requiresProfileSetup=true flag")
    void chatMemberIncompleteProfile_requiresProfileSetup() throws Exception {
        User incompleteUser = User.builder()
                .username("chat_incomplete")
                .email("chatincomplete@vegeai.com")
                .passwordHash(passwordEncoder.encode("hashed_pass"))
                .fullName("Chat Incomplete")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences(null) // Incomplete!
                .build();
        userRepository.save(incompleteUser);
        String token = jwtService.generateAccessToken(incompleteUser);

        AiChatRequest request = AiChatRequest.builder()
                .message("Tôi nên ăn gì hôm nay?")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requiresProfileSetup").value(true));
    }

    @Test
    @DisplayName("AI Chat: Chat with FRIDGE context screen and custom context data succeeds")
    void chatWithFridgeContext_success() throws Exception {
        AiChatRequest request = AiChatRequest.builder()
                .message("Tủ lạnh tôi có những món này thì nấu được gì?")
                .contextScreen("FRIDGE")
                .contextData("Cải ngọt, Nấm bào ngư, Đậu phụ")
                .build();

        mockMvc.perform(post("/api/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.requiresProfileSetup").value(false));
    }
}
