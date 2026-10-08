package com.vegeai.backend.modules.user.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.auth.repository.RefreshTokenRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

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
                double heightM = h / 100.0;
                double calculatedBmi = w / (heightM * heightM);
                double roundedBmi = BigDecimal.valueOf(calculatedBmi)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
                user.setBmi(roundedBmi);
            }
        }

        User saved = userRepository.save(user);
        log.info("[USER] Profile updated for userId={}", saved.getUserId());
        return UserProfileResponse.from(saved);
    }

    /**
     * Change password for the current authenticated user.
     * Revokes all active refresh tokens for enhanced session security.
     */
    @Transactional
    public void changePassword(String userIdStr, ChangePasswordRequest req) {
        User user = findUserById(userIdStr);

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        if (!req.getNewPassword().equals(req.getConfirmNewPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.SAME_PASSWORD_ERROR);
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        // Security best practice: Revoke all refresh tokens on password change
        refreshTokenRepository.revokeAllByUser(user);
        log.info("[USER] Password changed and refresh tokens revoked for userId={}", user.getUserId());
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
