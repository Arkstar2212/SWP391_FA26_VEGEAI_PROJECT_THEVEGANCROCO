package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.BmiCategory;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests — no Spring context. Expected values are worked out by hand in the comments.
 */
class NutritionCalculatorTest {

    private final NutritionCalculator calc = new NutritionCalculator();

    @Test
    @DisplayName("Male 30y 170cm 68kg MODERATE MAINTAIN — full calculation")
    void maleMaintain() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.MALE, 30, 170, 68, ActivityLevel.MODERATE, NutritionGoal.MAINTAIN, null));

        assertEquals(23.53, r.bmi());                    // 68 / 1.7^2
        assertEquals(BmiCategory.OVERWEIGHT, r.bmiCategory()); // Asia-Pacific: 23.0 - 24.9
        assertEquals(1598, r.bmr());                     // 680 + 1062.5 - 150 + 5 = 1597.5
        assertEquals(2477, r.tdee());                    // 1598 * 1.55 = 2476.9
        assertEquals(2477, r.dailyCaloriesTarget());
        assertFalse(r.customCalorieTarget());
        assertEquals(68, r.proteinTargetG());            // 68 kg * 1.0 g/kg
        assertEquals(69, r.fatTargetG());                // 2477 * 25% / 9 = 68.8
        assertEquals(396, r.carbsTargetG());             // (2477 - 272 - 621) / 4
        assertEquals(35, r.fiberTargetG());              // 2477 / 1000 * 14 = 34.7
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    @DisplayName("Female LOSE_WEIGHT below floor — raised to 1200 kcal with a warning")
    void femaleCalorieFloor() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.FEMALE, 25, 155, 45, ActivityLevel.SEDENTARY, NutritionGoal.LOSE_WEIGHT, null));

        assertEquals(1133, r.bmr());                     // 450 + 968.75 - 125 - 161 = 1132.75
        assertEquals(1360, r.tdee());                    // 1133 * 1.2 = 1359.6
        assertEquals(1200, r.dailyCaloriesTarget());     // 1360 - 500 = 860 -> floor 1200
        assertEquals(1, r.warnings().size());
    }

    @Test
    @DisplayName("Floor never exceeds TDEE — no deficit instead of a surplus")
    void floorCappedAtTdee() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.FEMALE, 60, 145, 38, ActivityLevel.SEDENTARY, NutritionGoal.LOSE_WEIGHT, null));
        // BMR = 380 + 906.25 - 300 - 161 = 825.25 -> 825; TDEE = 990
        assertEquals(990, r.tdee());
        assertEquals(990, r.dailyCaloriesTarget());
    }

    @Test
    @DisplayName("GAIN_MUSCLE adds 300 kcal and uses 1.6 g/kg protein")
    void gainMuscle() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.MALE, 22, 175, 70, ActivityLevel.ACTIVE, NutritionGoal.GAIN_MUSCLE, null));
        assertEquals(1689, r.bmr());                     // 700 + 1093.75 - 110 + 5 = 1688.75
        assertEquals(2914, r.tdee());                    // 1689 * 1.725 = 2913.5
        assertEquals(3214, r.dailyCaloriesTarget());
        assertEquals(112, r.proteinTargetG());           // 70 * 1.6
    }

    @Test
    @DisplayName("Custom calorie target overrides the computed one")
    void customTarget() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.MALE, 30, 170, 68, ActivityLevel.MODERATE, NutritionGoal.MAINTAIN, 2000));
        assertTrue(r.customCalorieTarget());
        assertEquals(2000, r.dailyCaloriesTarget());
        assertEquals(2477, r.tdee());
    }

    @Test
    @DisplayName("Protein is capped at 35% of calories")
    void proteinCap() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.MALE, 30, 180, 150, ActivityLevel.SEDENTARY, NutritionGoal.GAIN_MUSCLE, 1500));
        // uncapped: 150 * 1.6 = 240 g = 960 kcal (> 35% of 1500 = 525 kcal) -> 131 g
        assertEquals(131, r.proteinTargetG());
        assertTrue(r.carbsTargetG() >= 0);
    }

    @Test
    @DisplayName("Underweight + LOSE_WEIGHT and minors produce warnings")
    void warnings() {
        NutritionCalculation r = calc.calculate(new NutritionInput(
                Gender.FEMALE, 16, 165, 48, ActivityLevel.LIGHT, NutritionGoal.LOSE_WEIGHT, null));
        assertEquals(BmiCategory.UNDERWEIGHT, r.bmiCategory()); // 17.63
        assertTrue(r.warnings().size() >= 2);
    }

    @ParameterizedTest
    @CsvSource({"18.49,UNDERWEIGHT", "18.5,NORMAL", "22.99,NORMAL", "23.0,OVERWEIGHT", "24.99,OVERWEIGHT", "25.0,OBESE"})
    @DisplayName("BMI category boundaries (WHO Asia-Pacific)")
    void bmiBoundaries(double bmi, BmiCategory expected) {
        assertEquals(expected, BmiCategory.of(bmi));
    }

    @Test
    @DisplayName("Age is computed in full years")
    void age() {
        assertEquals(19, calc.calculateAge(LocalDate.of(2005, 10, 14), LocalDate.of(2025, 10, 13)));
        assertEquals(20, calc.calculateAge(LocalDate.of(2005, 10, 13), LocalDate.of(2025, 10, 13)));
    }

    @Test
    @DisplayName("Invalid input is rejected")
    void invalidInput() {
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(new NutritionInput(
                Gender.MALE, 10, 170, 68, ActivityLevel.MODERATE, NutritionGoal.MAINTAIN, null)));
        assertThrows(IllegalArgumentException.class, () -> calc.calculateBmi(0, 60));
    }
}
