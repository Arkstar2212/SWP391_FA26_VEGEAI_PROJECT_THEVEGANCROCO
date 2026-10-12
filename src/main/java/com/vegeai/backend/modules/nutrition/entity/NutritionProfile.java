package com.vegeai.backend.modules.nutrition.entity;

import com.vegeai.backend.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Nutrition profile (ERD: NUTRITION.PROFILES) — one per user.
 *
 * Inputs (gender, dob, height, weight, activity, goal) are entered by the user.
 * All targets (BMI, BMR, TDEE, calories, macros) are computed deterministically
 * by {@code NutritionCalculator} and stored as a snapshot so other modules
 * (meal plan, recipe scaling) can read them without recalculating.
 */
@Entity
@Table(name = "nutrition_profiles", indexes = {
        @Index(name = "idx_nutrition_profiles_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "nutrition_profile_id", updatable = false, nullable = false)
    private UUID nutritionProfileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // ── Inputs ───────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "height_cm", nullable = false)
    private Double heightCm;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 20)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false, length = 20)
    private NutritionGoal goal;

    /** Optional manual override of the daily calorie target (null = use computed value). */
    @Column(name = "custom_daily_calories")
    private Integer customDailyCalories;

    // ── Computed snapshot ────────────────────────────────────────────────────
    @Column(name = "bmi")
    private Double bmi;

    @Enumerated(EnumType.STRING)
    @Column(name = "bmi_category", length = 20)
    private BmiCategory bmiCategory;

    @Column(name = "bmr")
    private Integer bmr;

    @Column(name = "tdee")
    private Integer tdee;

    @Column(name = "daily_calories_target")
    private Integer dailyCaloriesTarget;

    @Column(name = "protein_target_g")
    private Integer proteinTargetG;

    @Column(name = "carbs_target_g")
    private Integer carbsTargetG;

    @Column(name = "fat_target_g")
    private Integer fatTargetG;

    @Column(name = "fiber_target_g")
    private Integer fiberTargetG;

    // ── Audit timestamps ─────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
