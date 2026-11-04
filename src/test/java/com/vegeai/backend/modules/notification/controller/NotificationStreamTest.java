package com.vegeai.backend.modules.notification.controller;

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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationStreamTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String token;

    @BeforeEach
    void setUp() {
        userRepository.findByUsernameIgnoreCase("sse_member").ifPresent(userRepository::delete);
        User member = userRepository.save(User.builder()
                .username("sse_member")
                .email("sse_member@vegeai.test")
                .passwordHash(passwordEncoder.encode("not-used"))
                .fullName("SSE Member")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build());
        token = "Bearer " + jwtService.generateAccessToken(member);
    }

    @Test
    @DisplayName("Anonymous clients cannot open the notification stream")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A signed-in member starts an SSE stream that stays open")
    void memberStartsSseStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/stream")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getRequest().getAsyncContext().complete();
    }
}
