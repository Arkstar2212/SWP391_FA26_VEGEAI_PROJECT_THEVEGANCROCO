package com.vegeai.backend.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.support.GeminiIntegrationTestSupport;
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
class AuthControllerTest extends GeminiIntegrationTestSupport {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.vegeai.backend.modules.community.service.CommunityDataInitializer communityDataInitializer;

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
        req.setUsername("vege_chef1");
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
        @DisplayName("Should fail with 400 when username has only letters (missing numbers)")
        void registerUsernameOnlyLettersFails() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setUsername("onlyletters");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                    .andExpect(jsonPath("$.data.username", notNullValue()));
        }

        @Test
        @DisplayName("Should fail with 400 when username has only digits (missing letters)")
        void registerUsernameOnlyDigitsFails() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setUsername("123456");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                    .andExpect(jsonPath("$.data.username", notNullValue()));
        }

        @Test
        @DisplayName("Should fail with 400 when phone number is not valid Vietnam format")
        void registerInvalidPhoneFails() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setPhoneNumber("0123456789"); // 01 is not a valid modern 10-digit Vietnam mobile prefix

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                    .andExpect(jsonPath("$.data.phoneNumber", notNullValue()));
        }

        @Test
        @DisplayName("Should accept valid +84 Vietnam phone number")
        void registerValidPlus84PhoneSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            req.setUsername("chef_plus84_1");
            req.setEmail("plus84@vegeai.com");
            req.setPhoneNumber("+84912345678");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
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

        @Test
        @DisplayName("Should block resend-otp if called too soon (< 30s cooldown)")
        void resendOtpCooldownBlocked() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            ResendOtpRequest resendReq = new ResendOtpRequest();
            resendReq.setEmail(req.getEmail());

            mockMvc.perform(post("/api/auth/resend-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resendReq)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OTP_RESEND_TOO_SOON.getCode()));
        }

        @Test
        @DisplayName("Should allow resend-otp after 30s cooldown has passed")
        void resendOtpSuccessAfterCooldown() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            User user = userRepository.findByEmailIgnoreCase(req.getEmail()).orElseThrow();
            var token = evtRepository.findLatestUnused(user, TokenPurpose.REGISTRATION).orElseThrow();
            token.setCreatedAt(java.time.LocalDateTime.now().minusSeconds(35));
            evtRepository.save(token);

            ResendOtpRequest resendReq = new ResendOtpRequest();
            resendReq.setEmail(req.getEmail());

            mockMvc.perform(post("/api/auth/resend-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resendReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Login & Security")
    class LoginTests {

        @Test
        @DisplayName("Should allow login if account is PENDING_VERIFICATION with unverified status")
        void loginPendingVerificationSucceedsWithUnverifiedStatus() throws Exception {
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
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
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
            loginReq.setUsernameOrEmail("vege_chef1");
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
        @DisplayName("Should refresh access token using valid refresh token cookie and rotate refresh token (RTR)")
        void refreshWithCookieSuccess() throws Exception {
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

            var loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            var initialCookie = loginResult.getResponse().getCookie("refreshToken");
            org.junit.jupiter.api.Assertions.assertNotNull(initialCookie);

            var refreshResult = mockMvc.perform(post("/api/auth/refresh")
                            .cookie(initialCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            var rotatedCookie = refreshResult.getResponse().getCookie("refreshToken");
            org.junit.jupiter.api.Assertions.assertNotNull(rotatedCookie);
            org.junit.jupiter.api.Assertions.assertNotEquals(initialCookie.getValue(), rotatedCookie.getValue(), "Refresh token must be rotated");

            // Verify old token is now marked as revoked in database
            var oldToken = refreshTokenRepository.findByToken(initialCookie.getValue()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertTrue(oldToken.isRevoked(), "Old refresh token must be revoked after rotation");
        }

        @Test
        @DisplayName("Should detect revoked token reuse and perform family revocation")
        void refreshRevokedTokenTriggersFamilyRevocation() throws Exception {
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

            var loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            var initialCookie = loginResult.getResponse().getCookie("refreshToken");

            // First refresh: rotates token (old token becomes revoked)
            mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                    .andExpect(status().isOk());

            // Second refresh with the OLD revoked token: REUSE DETECTED!
            mockMvc.perform(post("/api/auth/refresh").cookie(initialCookie))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.REFRESH_TOKEN_REVOKED.getCode()));

            // Verify all tokens for this user are now revoked
            var allTokens = refreshTokenRepository.findAll();
            org.junit.jupiter.api.Assertions.assertTrue(allTokens.stream().allMatch(com.vegeai.backend.modules.auth.entity.RefreshToken::isRevoked));
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

        @Test
        @DisplayName("Should reset password with valid OTP and allow login with new password")
        void resetPasswordSuccess() throws Exception {
            RegisterRequest req = createValidRegisterRequest();
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            User user = userRepository.findByEmailIgnoreCase(req.getEmail()).orElseThrow();
            user.setStatus(AccountStatus.ACTIVE);
            userRepository.save(user);

            // Request forgot password
            ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
            forgotReq.setEmail(req.getEmail());
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(forgotReq)))
                    .andExpect(status().isOk());

            var otpToken = evtRepository.findLatestUnused(user, TokenPurpose.FORGOT_PASSWORD).orElseThrow();

            String newSha256 = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";
            ResetPasswordRequest resetReq = new ResetPasswordRequest();
            resetReq.setEmail(req.getEmail());
            resetReq.setOtp(otpToken.getOtpCode());
            resetReq.setNewPassword(newSha256);
            resetReq.setConfirmNewPassword(newSha256);

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resetReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify login works with new password
            LoginRequest loginReq = new LoginRequest();
            loginReq.setUsernameOrEmail(req.getUsername());
            loginReq.setPassword(newSha256);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should fail reset-password when passwords mismatch")
        void resetPasswordMismatchFails() throws Exception {
            ResetPasswordRequest resetReq = new ResetPasswordRequest();
            resetReq.setEmail("chef@vegeai.com");
            resetReq.setOtp("123456");
            resetReq.setNewPassword(VALID_SHA256);
            resetReq.setConfirmNewPassword(MISMATCH_SHA256);

            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resetReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_MISMATCH.getCode()));
        }
    }

    @Nested
    @DisplayName("AI Chatbot Guest Access Guard (/api/ai/chat)")
    class AiChatbotQuotaTests {

        @Test
        @DisplayName("Guest is rejected on every request; AI no longer has a guest trial")
        void guestAiChatbotRequiresAuthentication() throws Exception {
            com.vegeai.backend.modules.ai.dto.AiChatRequest chatReq =
                    com.vegeai.backend.modules.ai.dto.AiChatRequest.builder()
                            .message("Can you recommend high-protein vegan breakfast?")
                            .build();

            String uniqueIp = "10.200." + ((System.currentTimeMillis() / 1000) % 200) + "." + ((System.nanoTime() / 1000) % 200);

            for (int i = 1; i <= 4; i++) {
                mockMvc.perform(post("/api/ai/chat")
                                .header("X-Forwarded-For", uniqueIp)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(chatReq)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.success").value(false));
            }

        }
    }
}
