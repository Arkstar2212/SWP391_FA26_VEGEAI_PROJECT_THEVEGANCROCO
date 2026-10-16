package com.vegeai.backend.modules.nutrition.dto;

import com.vegeai.backend.modules.nutrition.entity.ActivityLevel;
import com.vegeai.backend.modules.nutrition.entity.Gender;
import com.vegeai.backend.modules.nutrition.entity.NutritionGoal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Create / update the authenticated user's nutrition profile.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionProfileRequest {
    @Pattern(regexp = "^$|^(?:(?:\\+84|84|0)(?:3|5|7|8|9)\\d{8})$", message = "Số điện thoại không hợp lệ.")
    private String phoneNumber;
    @Pattern(regexp = "^$|VEGAN|VEGETARIAN|LACTO_OVO|LACTO|OVO|PESCATARIAN|FLEXITARIAN|RAW_VEGAN")
    private String dietaryPreferences;
    @Size(max = 255)
    private String allergies;

    @NotNull(message = "Gender is required.")
    @Schema(description = "Giới tính sinh học (dùng cho công thức BMR)", example = "MALE")
    private Gender gender;

    @NotNull(message = "Date of birth is required.")
    @Past(message = "Date of birth must be in the past.")
    @Schema(description = "Ngày sinh (tuổi hợp lệ: 13 - 100)", example = "2003-05-20")
    private LocalDate dateOfBirth;

    @NotNull(message = "Height is required.")
    @DecimalMin(value = "50.0", message = "Height must be at least 50 cm.")
    @DecimalMax(value = "280.0", message = "Height must be at most 280 cm.")
    @Schema(description = "Chiều cao (cm)", example = "170")
    private Double heightCm;

    @NotNull(message = "Weight is required.")
    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg.")
    @DecimalMax(value = "350.0", message = "Weight must be at most 350 kg.")
    @Schema(description = "Cân nặng (kg)", example = "68")
    private Double weightKg;

    @NotNull(message = "Activity level is required.")
    @Schema(description = "Mức độ vận động", example = "MODERATE")
    private ActivityLevel activityLevel;

    @NotNull(message = "Goal is required.")
    @Schema(description = "Mục tiêu", example = "MAINTAIN")
    private NutritionGoal goal;

    @Min(value = 800, message = "Custom daily calories must be at least 800 kcal.")
    @Max(value = 10000, message = "Custom daily calories must be at most 10,000 kcal.")
    @Schema(description = "(Tuỳ chọn) Tự đặt mục tiêu calo/ngày. Để null để hệ thống tự tính.", nullable = true)
    private Integer customDailyCalories;
}
