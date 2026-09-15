package com.example.swp391_fa26_vegeai_project.modules.nutrition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BmiResponse {
    private Double bmiValue;
    private String category; // UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE
    private String healthyWeightRange;
    private Integer estimatedDailyCalories;
    private String healthAdvice;
    private String disclaimer;
}
