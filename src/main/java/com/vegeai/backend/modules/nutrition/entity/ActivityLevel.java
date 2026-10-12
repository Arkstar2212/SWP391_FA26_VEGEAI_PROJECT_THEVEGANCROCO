package com.vegeai.backend.modules.nutrition.entity;

/**
 * Physical activity level with its standard TDEE multiplier (Harris-Benedict activity factors).
 */
public enum ActivityLevel {
    SEDENTARY(1.2, "Ít vận động (văn phòng, không tập)"),
    LIGHT(1.375, "Vận động nhẹ (tập 1-3 buổi/tuần)"),
    MODERATE(1.55, "Vận động vừa (tập 3-5 buổi/tuần)"),
    ACTIVE(1.725, "Vận động nhiều (tập 6-7 buổi/tuần)"),
    VERY_ACTIVE(1.9, "Vận động rất nhiều (lao động nặng / tập 2 lần/ngày)");

    private final double multiplier;
    private final String description;

    ActivityLevel(double multiplier, String description) {
        this.multiplier = multiplier;
        this.description = description;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public String getDescription() {
        return description;
    }
}
