package com.example.swp391_fa26_vegeai_project.modules.nutrition.service;

import com.example.swp391_fa26_vegeai_project.modules.nutrition.dto.BmiRequest;
import com.example.swp391_fa26_vegeai_project.modules.nutrition.dto.BmiResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class BmiCalculatorService {

    public BmiResponse calculateBmi(BmiRequest request) {
        double heightM = request.getHeightCm() / 100.0;
        double bmiRaw = request.getWeightKg() / (heightM * heightM);
        BigDecimal roundedBmi = BigDecimal.valueOf(bmiRaw).setScale(1, RoundingMode.HALF_UP);
        double bmi = roundedBmi.doubleValue();

        String category;
        String advice;
        if (bmi < 18.5) {
            category = "UNDERWEIGHT";
            advice = "Focus on nutrient-dense vegetarian foods like nuts, avocados, whole grains, and legumes.";
        } else if (bmi < 24.9) {
            category = "NORMAL";
            advice = "Maintain your balanced vegetarian diet with varied colorful vegetables, plant proteins, and hydration.";
        } else if (bmi < 29.9) {
            category = "OVERWEIGHT";
            advice = "Increase high-fiber leafy greens, reduce processed plant oils and refined sugars, and combine with daily physical activity.";
        } else {
            category = "OBESE";
            advice = "Consult with a registered healthcare dietitian for a personalized vegetarian calorie-deficit plan.";
        }

        double minNormalWeight = 18.5 * heightM * heightM;
        double maxNormalWeight = 24.9 * heightM * heightM;
        String weightRange = String.format("%.1f kg - %.1f kg", minNormalWeight, maxNormalWeight);

        // Harris-Benedict baseline estimation
        int baseCalories = 2000;
        if (request.getGender() != null && request.getGender().equalsIgnoreCase("FEMALE")) {
            baseCalories = 1800;
        }

        return BmiResponse.builder()
                .bmiValue(bmi)
                .category(category)
                .healthyWeightRange(weightRange)
                .estimatedDailyCalories(baseCalories)
                .healthAdvice(advice)
                .disclaimer("This calculation is for informational guidance only and does not replace certified medical consultation.")
                .build();
    }
}
