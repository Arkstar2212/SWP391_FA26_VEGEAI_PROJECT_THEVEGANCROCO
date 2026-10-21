package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.nutrition.dto.NutritionCalculateRequest;
import com.vegeai.backend.modules.nutrition.dto.NutritionProfileRequest;
import com.vegeai.backend.modules.nutrition.dto.NutritionProfileResponse;
import com.vegeai.backend.modules.nutrition.dto.NutritionTargetsResponse;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionProfile;
import com.vegeai.backend.modules.nutrition.repository.NutritionProfileRepository;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Nutrition profile use-cases. All math is delegated to {@link NutritionCalculator}.
 *
 * The profile is the source of truth for body metrics and targets. The legacy columns
 * on {@code users} (height_cm, weight_kg, bmi, daily_calorie_target, gender, date_of_birth)
 * are kept in sync so the existing /api/users/me response stays correct.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionService {

    private final NutritionProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final NutritionCalculator calculator;
    private final NotificationService notificationService;

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NutritionProfileResponse getMyProfile(String userIdStr) {
        UUID userId = parseUuid(userIdStr);
        NutritionProfile profile = profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NUTRITION_PROFILE_NOT_FOUND));
        int age = ageOf(profile.getDateOfBirth());
        NutritionCalculation calc = calculate(profile, age);
        return NutritionProfileResponse.from(profile, age, NutritionTargetsResponse.from(calc));
    }

    /** Stateless calculator for guests / onboarding preview — nothing is persisted. */
    public NutritionTargetsResponse calculatePreview(NutritionCalculateRequest req) {
        NutritionCalculation calc = calculator.calculate(new NutritionInput(
                req.getGender(), req.getAge(), req.getHeightCm(), req.getWeightKg(),
                req.getActivityLevel(), req.getGoal(), req.getCustomDailyCalories()));
        return NutritionTargetsResponse.from(calc);
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public NutritionProfileResponse upsertMyProfile(String userIdStr, NutritionProfileRequest req) {
        User user = userRepository.findById(parseUuid(userIdStr))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == com.vegeai.backend.modules.user.entity.AccountStatus.PENDING_VERIFICATION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_VERIFICATION,
                    "Tài khoản chưa được xác thực email. Vui lòng nhập mã OTP để kích hoạt tài khoản trước khi thiết lập hồ sơ dinh dưỡng.");
        }

        NutritionProfile profile = profileRepository.findByUser_UserId(user.getUserId())
                .orElseGet(() -> NutritionProfile.builder().user(user).build());
        boolean isNew = profile.getNutritionProfileId() == null;

        profile.setGender(req.getGender());
        profile.setDateOfBirth(req.getDateOfBirth());
        profile.setHeightCm(req.getHeightCm());
        profile.setWeightKg(req.getWeightKg());
        profile.setActivityLevel(req.getActivityLevel());
        profile.setGoal(req.getGoal());
        profile.setCustomDailyCalories(req.getCustomDailyCalories());
        if (req.getPhoneNumber() != null) user.setPhoneNumber(req.getPhoneNumber().trim());
        if (req.getDietaryPreferences() != null) user.setDietaryPreferences(req.getDietaryPreferences().trim());
        if (req.getAllergies() != null) user.setAllergies(req.getAllergies().trim().isEmpty() ? "NONE" : req.getAllergies().trim());

        int age = ageOf(profile.getDateOfBirth());
        NutritionCalculation calc = calculate(profile, age);
        applySnapshot(profile, calc);
        syncUserColumns(user, profile);

        NutritionProfile saved = profileRepository.save(profile);
        userRepository.save(user);

        log.info("[NUTRITION] Profile {} for userId={} — target={} kcal",
                isNew ? "created" : "updated", user.getUserId(), calc.dailyCaloriesTarget());
        // Confirm completed nutrition-profile changes without exposing any health values in the notice.
        notificationService.notifyUser(user, "NUTRITION_PROFILE_SAVED", "Hồ sơ dinh dưỡng đã được lưu",
                "Mục tiêu và thông tin dinh dưỡng cá nhân của bạn đã được cập nhật.", "/nutrition");
        return NutritionProfileResponse.from(saved, age, NutritionTargetsResponse.from(calc));
    }

    @Transactional
    public void deleteMyProfile(String userIdStr) {
        UUID userId = parseUuid(userIdStr);
        NutritionProfile profile = profileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NUTRITION_PROFILE_NOT_FOUND));
        profileRepository.delete(profile);
        log.info("[NUTRITION] Profile deleted for userId={}", userId);
        // Keep the user informed when their saved nutrition profile is removed.
        notificationService.notifyUser(profile.getUser(), "NUTRITION_PROFILE_DELETED", "Hồ sơ dinh dưỡng đã được xóa",
                "Hồ sơ dinh dưỡng cá nhân đã được xóa khỏi tài khoản của bạn.", "/nutrition");
    }

    /**
     * Called by {@code UserService} after body metrics are edited via PUT /api/users/me.
     * If the user already has a nutrition profile, copy the new values in and recompute targets.
     *
     * @param explicitCalorieTarget the dailyCalorieTarget sent in the request (becomes a custom override), or null
     * @return true if a profile existed and was recalculated
     */
    @Transactional
    public boolean syncFromUser(User user, Integer explicitCalorieTarget) {
        Optional<NutritionProfile> existing = profileRepository.findByUser_UserId(user.getUserId());
        if (existing.isEmpty()) {
            return false;
        }
        NutritionProfile profile = existing.get();

        if (user.getHeightCm() != null) profile.setHeightCm(user.getHeightCm());
        if (user.getWeightKg() != null) profile.setWeightKg(user.getWeightKg());
        if (user.getDateOfBirth() != null) profile.setDateOfBirth(user.getDateOfBirth());
        parseGender(user.getGender()).ifPresent(profile::setGender);
        if (explicitCalorieTarget != null) profile.setCustomDailyCalories(explicitCalorieTarget);

        NutritionCalculation calc = calculate(profile, ageOf(profile.getDateOfBirth()));
        applySnapshot(profile, calc);
        user.setBmi(calc.bmi());
        user.setDailyCalorieTarget(calc.dailyCaloriesTarget());
        profileRepository.save(profile);
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private NutritionCalculation calculate(NutritionProfile p, int age) {
        return calculator.calculate(new NutritionInput(
                p.getGender(), age, p.getHeightCm(), p.getWeightKg(),
                p.getActivityLevel(), p.getGoal(), p.getCustomDailyCalories()));
    }

    private int ageOf(LocalDate dateOfBirth) {
        int age = calculator.calculateAge(dateOfBirth, LocalDate.now());
        if (age < NutritionCalculator.MIN_AGE || age > NutritionCalculator.MAX_AGE) {
            throw new AppException(ErrorCode.NUTRITION_AGE_OUT_OF_RANGE);
        }
        return age;
    }

    private void applySnapshot(NutritionProfile p, NutritionCalculation c) {
        p.setBmi(c.bmi());
        p.setBmiCategory(c.bmiCategory());
        p.setBmr(c.bmr());
        p.setTdee(c.tdee());
        p.setDailyCaloriesTarget(c.dailyCaloriesTarget());
        p.setProteinTargetG(c.proteinTargetG());
        p.setCarbsTargetG(c.carbsTargetG());
        p.setFatTargetG(c.fatTargetG());
        p.setFiberTargetG(c.fiberTargetG());
    }

    private void syncUserColumns(User user, NutritionProfile p) {
        user.setGender(p.getGender().name());
        user.setDateOfBirth(p.getDateOfBirth());
        user.setHeightCm(p.getHeightCm());
        user.setWeightKg(p.getWeightKg());
        user.setBmi(p.getBmi());
        user.setDailyCalorieTarget(p.getDailyCaloriesTarget());
    }

    private Optional<Gender> parseGender(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Gender.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty(); // e.g. "OTHER" — keep the profile's existing value
        }
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid user ID format.");
        }
    }
}
