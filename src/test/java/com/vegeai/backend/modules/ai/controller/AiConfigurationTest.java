package com.vegeai.backend.modules.ai.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.user.repository.UserRepository;
import com.vegeai.backend.modules.user.entity.*;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.gemini.api-key=", "GEMINI_API_KEY="})
class AiConfigurationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    private String token;

    @MockBean(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private StringRedisTemplate redis;

    @BeforeEach
    void isolateGuestQuota() {
        when(redis.opsForValue().increment(anyString())).thenReturn(1L);
        String id=UUID.randomUUID().toString();
        User customer=users.saveAndFlush(User.builder().username("config-"+id).email(id+"@example.test")
                .fullName("Configuration fixture").passwordHash("test-only").role(UserRole.MEMBER).status(AccountStatus.ACTIVE).build());
        token=jwt.generateAccessToken(customer);
    }

    @Test
    void chatWithoutKeyReturnsUnavailableInsteadOfFakeAdvice() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What can I cook?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1706));
    }

    @Test
    void scanWithoutKeyReturnsUnavailableInsteadOfFakeIngredients() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "fridge.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/ai/fridge-scan").file(image).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1706));
    }
}
