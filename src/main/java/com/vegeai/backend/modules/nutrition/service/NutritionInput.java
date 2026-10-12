package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;

/**
 * Pure input for {@link NutritionCalculator}. {@code customDailyCalories} may be null.
 */
public record NutritionInput(
        Gender gender,
        int age,
        double heightCm,
        double weightKg,
        ActivityLevel activityLevel,
        NutritionGoal goal,
        Integer customDailyCalories
) {
}
