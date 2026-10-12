package com.vegeai.backend.modules.nutrition.dto;

import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Enum options for frontend dropdowns (gender, activity level, goal).
 */
@Getter
@Builder
public class NutritionOptionsResponse {

    private final List<String> genders;
    private final List<Option> activityLevels;
    private final List<Option> goals;

    public record Option(String code, String description, Double value) {
    }

    public static NutritionOptionsResponse create() {
        return NutritionOptionsResponse.builder()
                .genders(Arrays.stream(Gender.values()).map(Enum::name).toList())
                .activityLevels(Arrays.stream(ActivityLevel.values())
                        .map(a -> new Option(a.name(), a.getDescription(), a.getMultiplier()))
                        .toList())
                .goals(Arrays.stream(NutritionGoal.values())
                        .map(g -> new Option(g.name(), g.getDescription(), (double) g.getCalorieAdjustment()))
                        .toList())
                .build();
    }
}
