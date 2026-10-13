package com.vegeai.backend.modules.user.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.entity.EmailVerificationToken;
import com.vegeai.backend.modules.auth.entity.TokenPurpose;
import com.vegeai.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.vegeai.backend.modules.auth.service.EmailService;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
import com.vegeai.backend.modules.nutrition.service.NutritionCalculator;
import com.vegeai.backend.modules.nutrition.service.NutritionService;
import com.vegeai.backend.modules.user.dto.ChangePasswordRequest;
import com.vegeai.backend.modules.user.dto.PublicUserProfileResponse;
import com.vegeai.backend.modules.user.dto.UpdateUserProfileRequest;
import com.vegeai.backend.modules.user.dto.UserProfileResponse;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository evtRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final NutritionCalculator nutritionCalculator;
    private final NutritionService nutritionService;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Get the profile of the authenticated user (identified by userId from JWT).
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String userIdStr) {
        User user = findUserById(userIdStr);
        return UserProfileResponse.from(user);
    }

    /**
     * Update profile and recalculate deterministic BMI if height or weight changes.
     */
    @Transactional
    public UserProfileResponse updateMyProfile(String userIdStr, UpdateUserProfileRequest req) {
        User user = findUserById(userIdStr);

        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION,
                    "Tài khoản chưa được xác thực email. Vui lòng nhập mã OTP để xác thực tài khoản trước khi cập nhật hồ sơ cá nhân.");
        }

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            user.setFullName(req.getFullName().trim());
        }

        if (req.getPhoneNumber() != null) {
            String newPhone = req.getPhoneNumber().trim();
            if (!newPhone.isEmpty() && !newPhone.equalsIgnoreCase(user.getPhoneNumber())) {
                if (userRepository.existsByPhoneNumber(newPhone)) {
                    throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
                }
                user.setPhoneNumber(newPhone);
            } else if (newPhone.isEmpty()) {
                user.setPhoneNumber(null);
            }
        }

        if (req.getDateOfBirth() != null) {
            user.setDateOfBirth(req.getDateOfBirth());
        }

        if (req.getGender() != null) {
            user.setGender(req.getGender().trim());
        }

        if (req.getAddress() != null) {
            user.setAddress(req.getAddress().trim());
        }

        if (req.getDietaryPreferences() != null) {
            user.setDietaryPreferences(req.getDietaryPreferences().trim());
        }

        if (req.getAllergies() != null) {
            user.setAllergies(req.getAllergies().trim());
        }

        if (req.getDailyCalorieTarget() != null) {
            user.setDailyCalorieTarget(req.getDailyCalorieTarget());
        }

        // ── Deterministic BMI Calculation ─────────────────────────────────────
        boolean heightUpdated = req.getHeightCm() != null;
        boolean weightUpdated = req.getWeightKg() != null;

        if (heightUpdated) {
            user.setHeightCm(req.getHeightCm());
        }
        if (weightUpdated) {
            user.setWeightKg(req.getWeightKg());
        }

        if (heightUpdated || weightUpdated) {
            Double h = user.getHeightCm();
            Double w = user.getWeightKg();
            if (h != null && w != null && h > 0 && w > 0) {
                user.setBmi(nutritionCalculator.calculateBmi(h, w));
            }
        }

        // ── Keep the nutrition profile (if any) in sync with body metrics ─────
        boolean bodyMetricsChanged = heightUpdated || weightUpdated
                || req.getDateOfBirth() != null || req.getGender() != null
                || req.getDailyCalorieTarget() != null;
        if (bodyMetricsChanged) {
            nutritionService.syncFromUser(user, req.getDailyCalorieTarget());
        }

        User saved = userRepository.save(user);
        log.info("[USER] Profile updated for userId={}", saved.getUserId());
        return UserProfileResponse.from(saved);
    }

    /**
     * Change password for the current authenticated user.
     * Supports either currentPassword verification OR email OTP verification.
     * Revokes all active refresh tokens for enhanced session security.
     */
    @Transactional
    public void changePassword(String userIdStr, ChangePasswordRequest req) {
        User user = findUserById(userIdStr);

        if (!req.getNewPassword().equals(req.getConfirmNewPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.SAME_PASSWORD_ERROR);
        }

        // Verify either via OTP or via currentPassword
        if (req.getOtp() != null && !req.getOtp().isBlank()) {
            String otp = req.getOtp().trim();
            EmailVerificationToken token = evtRepository
                    .findLatestUnused(user, TokenPurpose.FORGOT_PASSWORD)
                    .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID,
                            "Mã OTP không hợp lệ hoặc không tìm thấy yêu cầu đổi mật khẩu."));

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
        } else if (req.getCurrentPassword() != null && !req.getCurrentPassword().isBlank()) {
            if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
                throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
            }
        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Vui lòng nhập mật khẩu hiện tại hoặc mã OTP để đổi mật khẩu.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Security best practice: Revoke all refresh tokens on password change
        refreshTokenRepository.revokeAllByUser(user);
        log.info("[USER] Password changed and refresh tokens revoked for userId={}", user.getUserId());
    }

    /**
     * Request OTP to change password for the authenticated user.
     */
    @Transactional
    public void requestPasswordChangeOtp(String userIdStr) {
        User user = findUserById(userIdStr);
        evtRepository.invalidatePreviousTokens(user, TokenPurpose.FORGOT_PASSWORD);
        int code = 100_000 + SECURE_RANDOM.nextInt(900_000);
        String otp = String.valueOf(code);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .otpCode(otp)
                .purpose(TokenPurpose.FORGOT_PASSWORD)
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .createdAt(LocalDateTime.now())
                .used(false)
                .build();
        evtRepository.save(token);

        emailService.sendForgotPasswordOtp(user, otp);
        log.info("[USER] Password change OTP sent to email={}", user.getEmail());
    }

    /**
     * User self-deactivation. Sets status to DISABLED and revokes tokens.
     */
    @Transactional
    public void deactivateMyAccount(String userIdStr) {
        User user = findUserById(userIdStr);
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUser(user);
        log.info("[USER] Account deactivated by owner userId={}", user.getUserId());
    }

    /**
     * Get safe public profile of any active user for community features.
     */
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, "User account is not accessible.");
        }

        return PublicUserProfileResponse.from(user);
    }

    private User findUserById(String userIdStr) {
        UUID userId = parseUuid(userIdStr);
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid user ID format.");
        }
    }
}
