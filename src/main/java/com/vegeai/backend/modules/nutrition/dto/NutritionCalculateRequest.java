package com.vegeai.backend.modules.nutrition.dto;

import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Stateless calculation request (guest calculator — nothing is saved).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionCalculateRequest {

    @NotNull(message = "Gender is required.")
    @Schema(example = "FEMALE")
    private Gender gender;

    @NotNull(message = "Age is required.")
    @Min(value = 13, message = "Age must be at least 13.")
    @Max(value = 100, message = "Age must be at most 100.")
    @Schema(example = "25")
    private Integer age;

    @NotNull(message = "Height is required.")
    @DecimalMin(value = "50.0", message = "Height must be at least 50 cm.")
    @DecimalMax(value = "280.0", message = "Height must be at most 280 cm.")
    @Schema(example = "158")
    private Double heightCm;

    @NotNull(message = "Weight is required.")
    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg.")
    @DecimalMax(value = "350.0", message = "Weight must be at most 350 kg.")
    @Schema(example = "55")
    private Double weightKg;

    @NotNull(message = "Activity level is required.")
    @Schema(example = "LIGHT")
    private ActivityLevel activityLevel;

    @NotNull(message = "Goal is required.")
    @Schema(example = "LOSE_WEIGHT")
    private NutritionGoal goal;

    @Min(value = 800, message = "Custom daily calories must be at least 800 kcal.")
    @Max(value = 10000, message = "Custom daily calories must be at most 10,000 kcal.")
    @Schema(nullable = true)
    private Integer customDailyCalories;
}
