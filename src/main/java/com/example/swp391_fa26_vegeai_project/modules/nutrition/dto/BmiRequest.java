package com.example.swp391_fa26_vegeai_project.modules.nutrition.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BmiRequest {
    @NotNull(message = "Height is required")
    @Min(value = 50, message = "Height must be at least 50 cm")
    @Max(value = 250, message = "Height must be less than 250 cm")
    private Double heightCm;

    @NotNull(message = "Weight is required")
    @Min(value = 20, message = "Weight must be at least 20 kg")
    @Max(value = 300, message = "Weight must be less than 300 kg")
    private Double weightKg;

    private Integer age;
    private String gender; // MALE, FEMALE
    private String activityLevel; // SEDENTARY, LIGHT, MODERATE, VERY_ACTIVE
}
