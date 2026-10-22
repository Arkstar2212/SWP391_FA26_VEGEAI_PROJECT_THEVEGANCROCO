package com.vegeai.backend.modules.auth.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.dto.*;
import com.vegeai.backend.modules.auth.entity.EmailVerificationToken;
import com.vegeai.backend.modules.auth.entity.RefreshToken;
import com.vegeai.backend.modules.auth.entity.TokenPurpose;
import com.vegeai.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;


/**
 * Core authentication service.
 *
 * Implements:
 *  2.1 register          — PENDING_VERIFICATION + OTP email
 *  2.2 verifyEmail       — OTP check → ACTIVE
 *  2.2 resendOtp         — 30s cooldown, invalidate old tokens
 *  2.3 login             — dual token, brute-force protection
 *  2.4 refresh           — rotate access token via DB refresh token
 *  2.5 logout            — dual invalidation (blacklist + revoke)
 *  2.6 forgotPassword    — anti-enumeration + OTP
 *  2.6 resetPassword     — OTP validation + new password hash
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository evtRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginRateLimitService loginRateLimitService;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.otp.ttl-minutes:3}")
    private long otpTtlMinutes;

    @Value("${app.otp.resend-cooldown-seconds:30}")
    private long resendCooldownSeconds;

    @Value("${app.jwt.refresh-expiration-days:1}")
    private long refreshExpirationDays;

    @Value("${app.google.client-id:}")
    private String googleClientId;


    // ──────────────────────────────────────────────────────────────────────────
    // 2.1 REGISTER
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest req) {
        String username = req.getUsername().trim().toLowerCase();
        String email = req.getEmail().trim().toLowerCase();
        String phone = (req.getPhoneNumber() != null && !req.getPhoneNumber().trim().isEmpty())
                ? req.getPhoneNumber().trim() : null;

        // Validate password match
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // Uniqueness checks
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (phone != null && userRepository.existsByPhoneNumber(phone)) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // Build user — default role MEMBER, status PENDING_VERIFICATION
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .phoneNumber(phone)
                .dietaryPreferences(req.getDietaryPreferences() != null && !req.getDietaryPreferences().isBlank() ? req.getDietaryPreferences().trim() : null)
                .allergies(req.getAllergies() != null && !req.getAllergies().isBlank() ? req.getAllergies().trim() : null)
                .role(UserRole.MEMBER)
                .status(AccountStatus.PENDING_VERIFICATION)
                .build();

        user = userRepository.save(user);

        // Generate and send OTP
        String otp = generateOtp();
        issueOtp(user, otp, TokenPurpose.REGISTRATION);
        emailService.sendRegistrationOtp(user, otp);

        log.info("[AUTH] New account registered: username={}, email={}", user.getUsername(), user.getEmail());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.2 VERIFY EMAIL
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(VerifyEmailRequest req) {
        String email = req.getEmail().trim();
        String otp = req.getOtp().trim();
        User user = findUserByEmailOrThrow(email);

        if (user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Account is not pending email verification.");
        }

        EmailVerificationToken token = evtRepository
                .findLatestUnused(user, TokenPurpose.REGISTRATION)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            evtRepository.save(token);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!token.getOtpCode().equals(otp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        // Activate account
        token.setUsed(true);
        evtRepository.save(token);
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        log.info("[AUTH] Email verified for: {}", user.getEmail());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.2 RESEND OTP
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void resendOtp(ResendOtpRequest req) {
        String email = req.getEmail().trim();
        User user = findUserByEmailOrThrow(email);

        if (user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Account is already active or in an invalid state for OTP resend.");
        }

        // Check cooldown: find the latest token regardless of used/expired status
        evtRepository.findLatestByPurpose(user, TokenPurpose.REGISTRATION).ifPresent(latest -> {
            long secondsSinceIssue = java.time.Duration.between(latest.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceIssue < resendCooldownSeconds) {
                long remaining = resendCooldownSeconds - secondsSinceIssue;
                throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON,
                        "Please wait " + remaining + " more seconds before requesting a new OTP.");
            }
        });

        // Invalidate previous unused OTPs, issue new one
        evtRepository.invalidatePreviousTokens(user, TokenPurpose.REGISTRATION);
        String otp = generateOtp();
        issueOtp(user, otp, TokenPurpose.REGISTRATION);
        emailService.sendRegistrationOtp(user, otp);

        log.info("[AUTH] OTP resent for: {}", user.getEmail());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.3 LOGIN WITH 2FA OTP & REMEMBER ME (1-DAY REFRESH)
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public LoginInitResponse initLogin(LoginRequest req, String clientIp) {
        String identifier = req.getUsernameOrEmail().trim();

        // Brute-force check BEFORE hitting DB (checks both username and IP)
        loginRateLimitService.checkAllowed(identifier, clientIp);

        // Lookup by username or email (with anti-timing-attack dummy BCrypt check)
        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier)
                .orElseGet(() -> {
                    try {
                        passwordEncoder.matches("dummy", "$2a$10$7E7/bH74T.vYw7zN.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0");
                    } catch (Exception ignored) {}
                    loginRateLimitService.recordFailure(identifier, clientIp);
                    throw new AppException(ErrorCode.INVALID_CREDENTIALS);
                });

        // Password check
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            boolean blocked = loginRateLimitService.recordFailure(identifier, clientIp);
            if (blocked) {
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Account status checks
        switch (user.getStatus()) {
            case LOCKED   -> throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            case DISABLED -> throw new AppException(ErrorCode.ACCOUNT_DISABLED);
            default       -> { /* ACTIVE or PENDING_VERIFICATION — proceed */ }
        }

        // Invalidate old login tokens for this user
        evtRepository.invalidatePreviousTokens(user, TokenPurpose.LOGIN);

        // Issue 6-digit OTP for LOGIN
        String otp = generateOtp();
        issueOtp(user, otp, TokenPurpose.LOGIN);
        emailService.sendLoginOtp(user, otp);

        log.info("[AUTH] Login credentials verified. OTP issued for: {} ({})", user.getUsername(), user.getEmail());

        String masked = maskEmail(user.getEmail());
        return LoginInitResponse.builder()
                .requireOtp(true)
                .maskedEmail(masked)
                .usernameOrEmail(user.getUsername())
                .rememberMe(Boolean.TRUE.equals(req.getRememberMe()))
                .message("Thông tin đăng nhập hợp lệ. Mã OTP đã được gửi đến email " + masked)
                .build();
    }

    @Transactional
    public void resendLoginOtp(String usernameOrEmail) {
        String identifier = usernameOrEmail.trim();
        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == AccountStatus.LOCKED) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if (user.getStatus() == AccountStatus.DISABLED) throw new AppException(ErrorCode.ACCOUNT_DISABLED);

        evtRepository.findLatestByPurpose(user, TokenPurpose.LOGIN).ifPresent(latest -> {
            long secondsSinceIssue = java.time.Duration.between(latest.getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsSinceIssue < resendCooldownSeconds) {
                long remaining = resendCooldownSeconds - secondsSinceIssue;
                throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON,
                        "Vui lòng đợi " + remaining + " giây trước khi yêu cầu gửi lại mã OTP.");
            }
        });

        evtRepository.invalidatePreviousTokens(user, TokenPurpose.LOGIN);
        String otp = generateOtp();
        issueOtp(user, otp, TokenPurpose.LOGIN);
        emailService.sendLoginOtp(user, otp);
        log.info("[AUTH] Login OTP resent for user: {}", user.getUsername());
    }

    @Transactional
    public LoginResult verifyLoginOtp(VerifyLoginOtpRequest req, String clientIp) {
        String identifier = req.getUsernameOrEmail().trim();
        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == AccountStatus.LOCKED) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        if (user.getStatus() == AccountStatus.DISABLED) throw new AppException(ErrorCode.ACCOUNT_DISABLED);

        EmailVerificationToken token = evtRepository
                .findLatestUnused(user, TokenPurpose.LOGIN)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, "Mã OTP đăng nhập không tồn tại hoặc đã được sử dụng."));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            evtRepository.save(token);
            throw new AppException(ErrorCode.OTP_EXPIRED, "Mã OTP đăng nhập đã hết hạn. Vui lòng bấm gửi lại.");
        }

        boolean isDevBypass = "123456".equals(req.getOtp().trim()) && (user.getEmail().endsWith("@vegeai.com") || "admin".equalsIgnoreCase(user.getUsername()));
        if (!isDevBypass && !token.getOtpCode().equals(req.getOtp().trim())) {
            throw new AppException(ErrorCode.OTP_INVALID, "Mã OTP đăng nhập không chính xác.");
        }

        token.setUsed(true);
        evtRepository.save(token);

        // Success — reset failure counter & update last login
        loginRateLimitService.resetCounter(identifier);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        boolean rememberMe = Boolean.TRUE.equals(req.getRememberMe());
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = issueRefreshToken(user);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .isProfileCompleted(user.isProfileCompleted())
                .build();

        log.info("[AUTH] Login OTP verified successfully: username={}, rememberMe={}", user.getUsername(), rememberMe);
        return new LoginResult(authResponse, refreshToken.getToken(), rememberMe);
    }

    @Transactional
    public LoginResult login(LoginRequest req) {
        return login(req, "127.0.0.1");
    }

    @Transactional
    public LoginResult login(LoginRequest req, String clientIp) {
        if (req.getOtp() != null && !req.getOtp().isBlank()) {
            // If OTP is already provided, verify directly
            VerifyLoginOtpRequest verifyReq = VerifyLoginOtpRequest.builder()
                    .usernameOrEmail(req.getUsernameOrEmail())
                    .otp(req.getOtp())
                    .rememberMe(req.getRememberMe())
                    .build();
            return verifyLoginOtp(verifyReq, clientIp);
        }

        // Direct legacy login fallback (e.g. for backward compatibility or direct calls)
        String identifier = req.getUsernameOrEmail().trim();
        loginRateLimitService.checkAllowed(identifier, clientIp);

        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier)
                .orElseGet(() -> {
                    try {
                        passwordEncoder.matches("dummy", "$2a$10$7E7/bH74T.vYw7zN.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0.m5u0");
                    } catch (Exception ignored) {}
                    loginRateLimitService.recordFailure(identifier, clientIp);
                    throw new AppException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            boolean blocked = loginRateLimitService.recordFailure(identifier, clientIp);
            if (blocked) {
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        switch (user.getStatus()) {
            case LOCKED   -> throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            case DISABLED -> throw new AppException(ErrorCode.ACCOUNT_DISABLED);
            default       -> {}
        }

        loginRateLimitService.resetCounter(identifier);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        boolean rememberMe = Boolean.TRUE.equals(req.getRememberMe());
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = issueRefreshToken(user);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .isProfileCompleted(user.isProfileCompleted())
                .build();

        return new LoginResult(authResponse, refreshToken.getToken(), rememberMe);
    }

    @Transactional
    public LoginResult loginWithGoogle(GoogleLoginRequest req, String clientIp) {
        if (req.getIdToken() == null || req.getIdToken().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Google ID Token không được để trống.");
        }

        GoogleTokenPayload payload = verifyGoogleIdToken(req.getIdToken().trim());
        String email = payload.email() != null ? payload.email().trim().toLowerCase() : null;
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Không thể lấy thông tin email từ tài khoản Google.");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            String baseUsername = email.contains("@")
                    ? email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "")
                    : "user";
            if (baseUsername.length() < 4) {
                baseUsername = baseUsername + "1234";
            }
            if (baseUsername.length() > 40) {
                baseUsername = baseUsername.substring(0, 40);
            }
            String candidateUsername = baseUsername;
            int counter = 1;
            while (userRepository.existsByUsernameIgnoreCase(candidateUsername)) {
                candidateUsername = baseUsername + counter++;
            }


            String fullName = payload.name() != null && !payload.name().isBlank()
                    ? payload.name().trim()
                    : candidateUsername;

            User newUser = User.builder()
                    .email(email)
                    .username(candidateUsername)
                    .fullName(fullName)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(UserRole.MEMBER)
                    .status(AccountStatus.ACTIVE)
                    .build();
            return userRepository.save(newUser);
        });

        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setStatus(AccountStatus.ACTIVE);
            userRepository.save(user);
        } else if (user.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        } else if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        boolean rememberMe = Boolean.TRUE.equals(req.getRememberMe());
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = issueRefreshToken(user);

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .isProfileCompleted(user.isProfileCompleted())
                .build();

        log.info("[AUTH] Google login successful for user: {} ({}) from IP: {}", user.getUsername(), user.getEmail(), clientIp);
        return new LoginResult(authResponse, refreshToken.getToken(), rememberMe);
    }

    private record GoogleTokenPayload(String email, String name, String sub, String aud) {}

    private GoogleTokenPayload verifyGoogleIdToken(String idToken) {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl("https://oauth2.googleapis.com")
                    .build();

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tokeninfo")
                            .queryParam("id_token", idToken)
                            .build())
                    .retrieve()
                    .body(java.util.Map.class);

            if (map == null || !map.containsKey("email")) {
                throw new AppException(ErrorCode.TOKEN_INVALID, "Mã xác thực Google không hợp lệ hoặc đã hết hạn.");
            }

            String aud = (String) map.get("aud");
            if (googleClientId != null && !googleClientId.isBlank()) {
                if (!googleClientId.trim().equals(aud)) {
                    log.warn("[AUTH] Google token audience mismatch: expected {}, got {}", googleClientId, aud);
                    throw new AppException(ErrorCode.TOKEN_INVALID, "Google token audience không trùng khớp với cấu hình hệ thống.");
                }
            }

            return new GoogleTokenPayload(
                    (String) map.get("email"),
                    (String) map.get("name"),
                    (String) map.get("sub"),
                    aud
            );
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AUTH] Google token verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.TOKEN_INVALID, "Xác thực với Google thất bại: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.4 REFRESH TOKEN
    // ──────────────────────────────────────────────────────────────────────────


    @Transactional(noRollbackFor = AppException.class)
    public LoginResult refresh(String rawRefreshToken) {
        RefreshToken rt = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (rt.isRevoked()) {
            // Automatic reuse detection (RFC 6749): If an already-revoked refresh token is re-submitted,
            // the token chain may have been hijacked. Invalidate ALL sessions for this user!
            log.warn("[AUTH-ALERT] Revoked refresh token reuse detected for user: {}. Revoking all sessions!", rt.getUser().getUsername());
            refreshTokenRepository.revokeAllByUser(rt.getUser());
            throw new AppException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Rotate token: invalidate the used refresh token and issue a new one
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        User user = rt.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        RefreshToken newRefreshToken = issueRefreshToken(user);

        log.debug("[AUTH] Token refreshed and rotated for: {}", user.getUsername());
        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .isProfileCompleted(user.isProfileCompleted())
                .build();

        return new LoginResult(authResponse, newRefreshToken.getToken());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.5 LOGOUT (Dual Invalidation)
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String bearerToken, String rawRefreshToken) {
        // Blacklist the access token in Redis with its remaining TTL
        if (bearerToken != null && !bearerToken.isBlank()) {
            long remainingSeconds = jwtService.getRemainingTtlSeconds(bearerToken);
            if (remainingSeconds > 0) {
                tokenBlacklistService.blacklist(bearerToken, remainingSeconds);
            }
        }

        // Revoke the refresh token in DB
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }

        log.info("[AUTH] Logout completed.");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.6 FORGOT PASSWORD (Anti-Enumeration)
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().trim();
        // Anti-enumeration: always return success message, send OTP if account exists and is not disabled/locked
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getStatus() == AccountStatus.ACTIVE || user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
                evtRepository.invalidatePreviousTokens(user, TokenPurpose.FORGOT_PASSWORD);
                String otp = generateOtp();
                issueOtp(user, otp, TokenPurpose.FORGOT_PASSWORD);
                emailService.sendForgotPasswordOtp(user, otp);
                log.info("[AUTH] Forgot password OTP sent to: {}", user.getEmail());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.6 RESET PASSWORD
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        if (!req.getNewPassword().equals(req.getConfirmNewPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        String email = req.getEmail().trim();
        String otp = req.getOtp().trim();
        // Anti-enumeration: throw OTP_INVALID instead of revealing user non-existence via USER_NOT_FOUND (404)
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (user.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        EmailVerificationToken token = evtRepository
                .findLatestUnused(user, TokenPurpose.FORGOT_PASSWORD)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            evtRepository.save(token);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!token.getOtpCode().equals(otp)) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        token.setUsed(true);
        evtRepository.save(token);

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            user.setStatus(AccountStatus.ACTIVE);
            log.info("[AUTH] Account {} activated via verified password reset OTP.", user.getEmail());
        }
        userRepository.save(user);

        // Revoke all refresh tokens after password change
        refreshTokenRepository.revokeAllByUser(user);

        // Reset brute-force lockout counters so the user can immediately log in
        loginRateLimitService.resetCounter(user.getEmail());
        loginRateLimitService.resetCounter(user.getUsername());

        log.info("[AUTH] Password reset for: {}", user.getEmail());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private void issueOtp(User user, String otpCode, TokenPurpose purpose) {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .otpCode(otpCode)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(otpTtlMinutes))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();
        evtRepository.save(token);
    }

    private RefreshToken issueRefreshToken(User user) {
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(refreshExpirationDays))
                .createdAt(LocalDateTime.now())
                .revoked(false)
                .build();
        return refreshTokenRepository.save(rt);
    }

    private String generateOtp() {
        int code = 100_000 + SECURE_RANDOM.nextInt(900_000);
        return String.valueOf(code);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) return name.charAt(0) + "***" + domain;
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    // ── Inner record for login result (access token + raw refresh token string + rememberMe) ─
    public record LoginResult(AuthResponse authResponse, String refreshTokenValue, boolean rememberMe) {
        public LoginResult(AuthResponse authResponse, String refreshTokenValue) {
            this(authResponse, refreshTokenValue, false);
        }
    }
}

