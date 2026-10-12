package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.modules.nutrition.entity.BmiCategory;

import java.util.List;

/**
 * Deterministic output of {@link NutritionCalculator}.
 */
public record NutritionCalculation(
        double bmi,
        BmiCategory bmiCategory,
        int bmr,
        int tdee,
        int dailyCaloriesTarget,
        boolean customCalorieTarget,
        int proteinTargetG,
        int carbsTargetG,
        int fatTargetG,
        int fiberTargetG,
        List<String> warnings
) {
}
