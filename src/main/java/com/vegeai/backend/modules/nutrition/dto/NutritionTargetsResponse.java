package com.vegeai.backend.modules.nutrition.dto;

import com.vegeai.backend.modules.nutrition.service.NutritionCalculation;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Computed nutrition targets returned to the client.
 */
@Getter
@Builder
public class NutritionTargetsResponse {

    private final Double bmi;
    private final String bmiCategory;
    private final String bmiCategoryLabel;
    private final Integer bmr;
    private final Integer tdee;
    private final Integer dailyCaloriesTarget;
    private final boolean customCalorieTarget;
    private final Integer proteinTargetG;
    private final Integer carbsTargetG;
    private final Integer fatTargetG;
    private final Integer fiberTargetG;
    private final List<String> warnings;

    public static NutritionTargetsResponse from(NutritionCalculation c) {
        return NutritionTargetsResponse.builder()
                .bmi(c.bmi())
                .bmiCategory(c.bmiCategory().name())
                .bmiCategoryLabel(c.bmiCategory().getLabel())
                .bmr(c.bmr())
                .tdee(c.tdee())
                .dailyCaloriesTarget(c.dailyCaloriesTarget())
                .customCalorieTarget(c.customCalorieTarget())
                .proteinTargetG(c.proteinTargetG())
                .carbsTargetG(c.carbsTargetG())
                .fatTargetG(c.fatTargetG())
                .fiberTargetG(c.fiberTargetG())
                .warnings(c.warnings())
                .build();
    }
}
