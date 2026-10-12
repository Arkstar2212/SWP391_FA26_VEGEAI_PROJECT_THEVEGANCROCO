package com.vegeai.backend.modules.nutrition.entity;

/**
 * BMI classification using the WHO Asia-Pacific (IDI &amp; WPRO) cut-offs,
 * which are the ones applied in Vietnam.
 */
public enum BmiCategory {
    UNDERWEIGHT("Gầy"),          // < 18.5
    NORMAL("Bình thường"),       // 18.5 - 22.9
    OVERWEIGHT("Thừa cân"),      // 23.0 - 24.9
    OBESE("Béo phì");            // >= 25.0

    private final String label;

    BmiCategory(String label) {
        this.label = label;
    }

    public static BmiCategory of(double bmi) {
        if (bmi < 18.5) return UNDERWEIGHT;
        if (bmi < 23.0) return NORMAL;
        if (bmi < 25.0) return OVERWEIGHT;
        return OBESE;
    }

    public String getLabel() {
        return label;
    }
}
