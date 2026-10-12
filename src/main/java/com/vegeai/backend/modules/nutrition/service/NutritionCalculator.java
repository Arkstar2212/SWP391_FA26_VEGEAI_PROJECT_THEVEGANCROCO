package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.modules.nutrition.entity.BmiCategory;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic nutrition math — no AI, no database, no randomness.
 * The same input always produces the same output, so results are auditable and unit-testable.
 *
 * <ul>
 *   <li>BMI = weight(kg) / height(m)^2, classified with WHO Asia-Pacific cut-offs.</li>
 *   <li>BMR — Mifflin-St Jeor (1990): 10·kg + 6.25·cm − 5·age + 5 (male) / −161 (female).</li>
 *   <li>TDEE = BMR × activity multiplier.</li>
 *   <li>Daily calories = TDEE + goal adjustment, never below a safety floor
 *       (1,200 kcal female / 1,500 kcal male) unless TDEE itself is lower.</li>
 *   <li>Protein = g/kg by goal (capped at 35% of calories); fat = 25% of calories;
 *       carbs = remaining calories; fiber = 14 g per 1,000 kcal.</li>
 * </ul>
 */
@Component
public class NutritionCalculator {

    public static final int MIN_AGE = 13;
    public static final int MAX_AGE = 100;

    static final int FEMALE_CALORIE_FLOOR = 1200;
    static final int MALE_CALORIE_FLOOR = 1500;
    static final double FAT_CALORIE_RATIO = 0.25;
    static final double MAX_PROTEIN_CALORIE_RATIO = 0.35;
    static final double FIBER_GRAMS_PER_1000_KCAL = 14.0;

    private static final int KCAL_PER_G_PROTEIN = 4;
    private static final int KCAL_PER_G_CARBS = 4;
    private static final int KCAL_PER_G_FAT = 9;

    // ── Public API ───────────────────────────────────────────────────────────

    public NutritionCalculation calculate(NutritionInput in) {
        validate(in);
        List<String> warnings = new ArrayList<>();

        double bmi = calculateBmi(in.heightCm(), in.weightKg());
        BmiCategory bmiCategory = BmiCategory.of(bmi);
        int bmr = calculateBmr(in.gender(), in.age(), in.heightCm(), in.weightKg());
        int tdee = (int) Math.round(bmr * in.activityLevel().getMultiplier());

        int floor = in.gender() == Gender.FEMALE ? FEMALE_CALORIE_FLOOR : MALE_CALORIE_FLOOR;
        boolean custom = in.customDailyCalories() != null;
        int target;

        if (custom) {
            target = in.customDailyCalories();
            if (target < floor) {
                warnings.add("Mục tiêu calo tự đặt (" + target + " kcal) thấp hơn mức an toàn tối thiểu "
                        + floor + " kcal. Nên tham khảo chuyên gia dinh dưỡng.");
            }
        } else {
            target = tdee + in.goal().getCalorieAdjustment();
            if (target < floor) {
                int adjusted = Math.min(floor, tdee);
                warnings.add("Mục tiêu calo được nâng từ " + target + " lên " + adjusted
                        + " kcal để không thấp hơn mức an toàn tối thiểu.");
                target = adjusted;
            }
        }

        if (in.goal() == NutritionGoal.LOSE_WEIGHT && bmiCategory == BmiCategory.UNDERWEIGHT) {
            warnings.add("BMI đang ở mức Gầy — không khuyến nghị mục tiêu giảm cân.");
        }
        if (in.age() < 18) {
            warnings.add("Người dùng dưới 18 tuổi: công thức Mifflin-St Jeor được xây dựng cho người trưởng thành, "
                    + "kết quả chỉ mang tính tham khảo.");
        }

        // ── Macros ──────────────────────────────────────────────────────────
        int proteinG = (int) Math.round(in.weightKg() * in.goal().getProteinGramsPerKg());
        int maxProteinG = (int) Math.floor(target * MAX_PROTEIN_CALORIE_RATIO / KCAL_PER_G_PROTEIN);
        proteinG = Math.min(proteinG, maxProteinG);

        int fatG = (int) Math.round(target * FAT_CALORIE_RATIO / KCAL_PER_G_FAT);
        int remainingKcal = target - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT;
        int carbsG = Math.max(0, (int) Math.round((double) remainingKcal / KCAL_PER_G_CARBS));
        int fiberG = (int) Math.round(target / 1000.0 * FIBER_GRAMS_PER_1000_KCAL);

        return new NutritionCalculation(bmi, bmiCategory, bmr, tdee, target, custom,
                proteinG, carbsG, fatG, fiberG, List.copyOf(warnings));
    }

    /** BMI rounded to 2 decimals (HALF_UP). */
    public double calculateBmi(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) {
            throw new IllegalArgumentException("Height and weight must be positive.");
        }
        double heightM = heightCm / 100.0;
        return BigDecimal.valueOf(weightKg / (heightM * heightM))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Mifflin-St Jeor BMR in kcal/day, rounded to the nearest integer. */
    public int calculateBmr(Gender gender, int age, double heightCm, double weightKg) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * age;
        double bmr = gender == Gender.MALE ? base + 5 : base - 161;
        return (int) Math.round(bmr);
    }

    /** Age in full years on {@code today}. */
    public int calculateAge(LocalDate dateOfBirth, LocalDate today) {
        return Period.between(dateOfBirth, today).getYears();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void validate(NutritionInput in) {
        if (in.gender() == null || in.activityLevel() == null || in.goal() == null) {
            throw new IllegalArgumentException("Gender, activity level and goal are required.");
        }
        if (in.age() < MIN_AGE || in.age() > MAX_AGE) {
            throw new IllegalArgumentException("Age must be between " + MIN_AGE + " and " + MAX_AGE + ".");
        }
        if (in.heightCm() <= 0 || in.weightKg() <= 0) {
            throw new IllegalArgumentException("Height and weight must be positive.");
        }
    }
}
