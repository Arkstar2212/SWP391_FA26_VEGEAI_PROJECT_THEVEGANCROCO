package com.vegeai.backend.modules.ai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.gemini.api-key=", "GEMINI_API_KEY="})
class AiConfigurationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void chatWithoutKeyReturnsUnavailableInsteadOfFakeAdvice() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("X-Forwarded-For", "10.251.10.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What can I cook?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1706));
    }

    @Test
    void scanWithoutKeyReturnsUnavailableInsteadOfFakeIngredients() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "fridge.jpg", "image/jpeg", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/ai/fridge-scan").file(image))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1706));
    }
}
