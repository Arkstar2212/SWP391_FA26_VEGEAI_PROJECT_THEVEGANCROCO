package com.vegeai.backend.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.dto.*;
import com.vegeai.backend.modules.auth.entity.TokenPurpose;
import com.vegeai.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository evtRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        evtRepository.deleteAll();
        userRepository.deleteAll();
    }

    private static final String VALID_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String MISMATCH_SHA256 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

    private RegisterRequest createValidRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("vege_chef");
        req.setEmail("chef@vegeai.com");
        req.setPassword(VALID_SHA256);
        req.setConfirmPassword(VALID_SHA256);
        req.setFullName("Nguyen Van A");
        req.setPhoneNumber("0912345678");
        req.setDietaryPreferences("VEGAN");
        req.setAllergies("PEANUTS");
        return req;
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class RegisterTests {

        @Test
        @DisplayName("Should successfully register account with valid input")
        void registerSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message", containsString("Account created successfully")));
        }

        @Test
        @DisplayName("Should successfully register with empty phone number (optional field)")
        void registerWithEmptyPhoneSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setPhoneNumber(""); // Frontend sending empty string

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should fail with 400 when password is plain text (must be 64-char SHA-256 hex)")
        void registerPlainTextPasswordFails() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setPassword("PlainPassword123!");
            req.setConfirmPassword("PlainPassword123!");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        }

        @Test
        @DisplayName("Should fail with 400 when password and confirmPassword mismatch")
        void registerPasswordMismatch() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setConfirmPassword(MISMATCH_SHA256);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_MISMATCH.getCode()));
        }

        @Test
        @DisplayName("Should fail with 400 when username has invalid characters (misinput)")
        void registerInvalidUsername() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setUsername("invalid user!");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                    .andExpect(jsonPath("$.data.username", notNullValue()));
        }

        @Test
        @DisplayName("Should fail with 400 when email is invalid (misinput)")
        void registerInvalidEmail() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setEmail("not-an-email");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                    .andExpect(jsonPath("$.data.email", notNullValue()));
        }

        @Test
        @DisplayName("Should fail with 400 when password is too short (< 8 chars)")
        void registerShortPassword() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setPassword("123");
            req.setConfirmPassword("123");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        }

        @Test
        @DisplayName("Should fail with 409 Conflict when username already exists")
        void registerDuplicateUsername() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            RegisterRequest dup = createValidRegisterRequest();
            dup.setEmail("another@vegeai.com");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dup)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.USERNAME_ALREADY_EXISTS.getCode()));
        }
    }

    @Nested
    @DisplayName("Misinput & General Exception Handling")
    class MisinputExceptionTests {

        @Test
        @DisplayName("Should return 400 MALFORMED_JSON when payload is broken JSON syntax")
        void malformedJsonReturns400() throws Exception {
            String malformedJson = "{\"username\": \"broken\", ";

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.MALFORMED_JSON.getCode()))
                    .andExpect(jsonPath("$.message", containsString("Malformed JSON")));
        }

        @Test
        @DisplayName("Should return 405 METHOD_NOT_ALLOWED when wrong HTTP method is used")
        void wrongMethodReturns405() throws Exception {
            mockMvc.perform(get("/api/auth/login"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.getCode()));
        }

        @Test
        @DisplayName("Should return 415 UNSUPPORTED_MEDIA_TYPE when wrong Content-Type is provided")
        void wrongMediaTypeReturns415() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("plain text"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()));
        }
    }

    @Nested
    @DisplayName("Email Verification & OTP")
    class VerifyEmailTests {

        @Test
        @DisplayName("Should verify email successfully with valid OTP")
        void verifyEmailSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            User user = userRepository.findByEmailIgnoreCase(req.getEmail()).orElseThrow();
            var token = evtRepository.findLatestUnused(user, TokenPurpose.REGISTRATION).orElseThrow();

            VerifyEmailRequest verifyReq = new VerifyEmailRequest();
            verifyReq.setEmail(req.getEmail());
            verifyReq.setOtp(token.getOtpCode());

            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            User updatedUser = userRepository.findByEmailIgnoreCase(req.getEmail()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(AccountStatus.ACTIVE, updatedUser.getStatus());
        }

        @Test
        @DisplayName("Should reject invalid OTP with 400 OTP_INVALID")
        void verifyEmailInvalidOtp() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            VerifyEmailRequest verifyReq = new VerifyEmailRequest();
            verifyReq.setEmail(req.getEmail());
            verifyReq.setOtp("000000");

            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(verifyReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OTP_INVALID.getCode()));
        }
    }

    @Nested
    @DisplayName("Login & Security")
    class LoginTests {

        @Test
        @DisplayName("Should prevent login if account is PENDING_VERIFICATION")
        void loginPendingVerificationFails() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail(req.getUsername());
            loginReq.setPassword(req.getPassword());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_PENDING_VERIFICATION.getCode()));
        }

        @Test
        @DisplayName("Should login successfully when active and credentials match")
        void loginSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            User user = userRepository.findByEmailIgnoreCase(req.getEmail()).orElseThrow();
            user.setStatus(AccountStatus.ACTIVE);
            userRepository.save(user);

            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail(req.getUsername());
            loginReq.setPassword(req.getPassword());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                    .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        @DisplayName("Should fail login with 400 when password is not 64-char SHA-256 hex")
        void loginPlainTextPasswordFails() throws Exception {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail("vege_chef");
            loginReq.setPassword("PlainPassword123!");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
        }

        @Test
        @DisplayName("Should fail login with 401 on wrong password (valid SHA-256)")
        void loginWrongPasswordFails() throws Exception {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail("nonexistent_user");
            loginReq.setPassword(MISMATCH_SHA256);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));
        }
    }

    @Nested
    @DisplayName("Token Refresh & Logout")
    class RefreshAndLogoutTests {

        @Test
        @DisplayName("Should fail with 401 when refresh token is missing")
        void refreshWithoutTokenFails() throws Exception {
            mockMvc.perform(post("/api/auth/refresh"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_TOKEN_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("Should logout cleanly and clear refresh cookie")
        void logoutSuccess() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(header().exists("Set-Cookie"));
        }
    }

    @Nested
    @DisplayName("Password Reset")
    class PasswordResetTests {

        @Test
        @DisplayName("Forgot password should always return 200 (anti-enumeration)")
        void forgotPasswordAntiEnumeration() throws Exception {
            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("nonexistent@vegeai.com");

            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message", containsString("If the email exists")));
        }
    }

    @Nested
    @DisplayName("AI Chatbot Guest Quota Guard (/api/ai/chat)")
    class AiChatbotQuotaTests {

        @Test
        @DisplayName("Guest should be allowed 3 requests then blocked with 429 on 4th request")
        void guestAiChatbotQuotaEnforced() throws Exception {
            com.vegeai.backend.modules.ai.dto.AiChatRequest chatReq =
                    com.vegeai.backend.modules.ai.dto.AiChatRequest.builder()
                            .message("Can you recommend high-protein vegan breakfast?")
                            .build();

            String uniqueIp = "192.168.10.99";

            // Requests 1, 2, 3 should succeed
            for (int i = 1; i <= 3; i++) {
                mockMvc.perform(post("/api/ai/chat")
                                .header("X-Forwarded-For", uniqueIp)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(chatReq)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true));
            }

            // Request 4 must be blocked with HTTP 429 Too Many Requests
            mockMvc.perform(post("/api/ai/chat")
                            .header("X-Forwarded-For", uniqueIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(chatReq)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.GUEST_AI_QUOTA_EXCEEDED.getCode()))
                    .andExpect(jsonPath("$.message", containsString("Guest trial limit reached")));
        }
    }
}
