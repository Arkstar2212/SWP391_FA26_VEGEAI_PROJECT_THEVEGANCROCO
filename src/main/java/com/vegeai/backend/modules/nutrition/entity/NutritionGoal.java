package com.vegeai.backend.modules.nutrition.entity;

/**
 * Body-composition goal. Each goal adjusts TDEE by a fixed kcal delta
 * and sets the protein intake per kg of body weight.
 *
 * Protein targets are slightly higher than omnivore guidelines to compensate
 * for the lower digestibility of plant protein.
 */
public enum NutritionGoal {
    LOSE_WEIGHT(-500, 1.2, "Giảm cân (~0.5 kg/tuần)"),
    MAINTAIN(0, 1.0, "Giữ cân"),
    GAIN_MUSCLE(300, 1.6, "Tăng cơ");

    private final int calorieAdjustment;
    private final double proteinGramsPerKg;
    private final String description;

    NutritionGoal(int calorieAdjustment, double proteinGramsPerKg, String description) {
        this.calorieAdjustment = calorieAdjustment;
        this.proteinGramsPerKg = proteinGramsPerKg;
        this.description = description;
    }

    public int getCalorieAdjustment() {
        return calorieAdjustment;
    }

    public double getProteinGramsPerKg() {
        return proteinGramsPerKg;
    }

    public String getDescription() {
        return description;
    }
}
