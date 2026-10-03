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

    // ──────────────────────────────────────────────────────────────────────────
    // 2.1 REGISTER
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest req) {
        // Validate password match
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // Uniqueness checks
        if (userRepository.existsByUsernameIgnoreCase(req.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmailIgnoreCase(req.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // Build user — default role MEMBER, status PENDING_VERIFICATION
        User user = User.builder()
                .username(req.getUsername().trim().toLowerCase())
                .email(req.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .phoneNumber(req.getPhoneNumber())
                .dietaryPreferences(req.getDietaryPreferences())
                .allergies(req.getAllergies())
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
        User user = findUserByEmailOrThrow(req.getEmail());

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

        if (!token.getOtpCode().equals(req.getOtp())) {
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
        User user = findUserByEmailOrThrow(req.getEmail());

        if (user.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Account is already active or in an invalid state for OTP resend.");
        }

        // Check cooldown: find the latest token regardless of used/expired status
        evtRepository.findLatestUnused(user, TokenPurpose.REGISTRATION).ifPresent(latest -> {
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
    // 2.3 LOGIN
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResult login(LoginRequest req) {
        String identifier = req.getUsernameOrEmail().trim();

        // Brute-force check BEFORE hitting DB
        if (loginRateLimitService.isBlocked(identifier)) {
            throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
        }

        // Lookup by username or email
        User user = userRepository.findByUsernameOrEmailIgnoreCase(identifier)
                .orElseThrow(() -> {
                    loginRateLimitService.recordFailure(identifier);
                    return new AppException(ErrorCode.INVALID_CREDENTIALS);
                });

        // Password check
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            boolean blocked = loginRateLimitService.recordFailure(identifier);
            if (blocked) {
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Account status checks
        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION);
            case LOCKED               -> throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            case DISABLED             -> throw new AppException(ErrorCode.ACCOUNT_DISABLED);
            default                   -> { /* ACTIVE — proceed */ }
        }

        // Success — reset failure counter
        loginRateLimitService.resetCounter(identifier);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Issue tokens
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
                .build();

        log.info("[AUTH] Login successful: username={}", user.getUsername());
        return new LoginResult(authResponse, refreshToken.getToken());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.4 REFRESH TOKEN
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken rt = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (rt.isRevoked()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (rt.getExpiresAt().isBefore(LocalDateTime.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = rt.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);

        log.debug("[AUTH] Token refreshed for: {}", user.getUsername());
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getExpirationMinutes() * 60)
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2.5 LOGOUT (Dual Invalidation)
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String bearerToken, String rawRefreshToken) {
        // Blacklist the access token — compute its expiry epoch second for proper TTL
        if (bearerToken != null && !bearerToken.isBlank()) {
            long remainingSeconds = jwtService.getRemainingTtlSeconds(bearerToken);
            if (remainingSeconds > 0) {
                long expiryEpoch = java.time.Instant.now().getEpochSecond() + remainingSeconds;
                tokenBlacklistService.blacklist(bearerToken, expiryEpoch);
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
        // Anti-enumeration: always return success message, only send if account exists + ACTIVE
        userRepository.findByEmailIgnoreCase(req.getEmail()).ifPresent(user -> {
            if (user.getStatus() == AccountStatus.ACTIVE) {
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

        User user = findUserByEmailOrThrow(req.getEmail());

        EmailVerificationToken token = evtRepository
                .findLatestUnused(user, TokenPurpose.FORGOT_PASSWORD)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setUsed(true);
            evtRepository.save(token);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }

        if (!token.getOtpCode().equals(req.getOtp())) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        token.setUsed(true);
        evtRepository.save(token);

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens after password change
        refreshTokenRepository.revokeAllByUser(user);

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

    // ── Inner record for login result (access token + raw refresh token string) ─
    public record LoginResult(AuthResponse authResponse, String refreshTokenValue) {}
}
