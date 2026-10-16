package com.vegeai.backend.modules.nutrition.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vegeai.backend.modules.nutrition.entity.NutritionProfile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The user's saved nutrition profile: inputs + computed targets.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NutritionProfileResponse {
    private final String phoneNumber;
    private final String dietaryPreferences;
    private final String allergies;

    private final UUID nutritionProfileId;
    private final UUID userId;
    private final String gender;
    private final LocalDate dateOfBirth;
    private final Integer age;
    private final Double heightCm;
    private final Double weightKg;
    private final String activityLevel;
    private final String goal;
    private final Integer customDailyCalories;
    private final NutritionTargetsResponse targets;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public static NutritionProfileResponse from(NutritionProfile p, int age, NutritionTargetsResponse targets) {
        return NutritionProfileResponse.builder()
                .phoneNumber(p.getUser().getPhoneNumber())
                .dietaryPreferences(p.getUser().getDietaryPreferences())
                .allergies(p.getUser().getAllergies())
                .nutritionProfileId(p.getNutritionProfileId())
                .userId(p.getUser().getUserId())
                .gender(p.getGender().name())
                .dateOfBirth(p.getDateOfBirth())
                .age(age)
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .activityLevel(p.getActivityLevel().name())
                .goal(p.getGoal().name())
                .customDailyCalories(p.getCustomDailyCalories())
                .targets(targets)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
