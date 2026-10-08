package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
    @Schema(description = "Full name", example = "Tran Van Chay")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+]{9,15}$", message = "Phone number must be valid (9-15 digits).")
    @Schema(description = "Phone number", example = "+84901234567")
    private String phoneNumber;

    @Schema(description = "Date of birth", example = "2000-01-15")
    private LocalDate dateOfBirth;

    @Size(max = 10, message = "Gender must be at most 10 characters.")
    @Schema(description = "Gender (e.g. MALE, FEMALE, OTHER)", example = "MALE")
    private String gender;

    @Size(max = 255, message = "Address must be at most 255 characters.")
    @Schema(description = "Residential address", example = "123 Nguyen Trai, District 1, HCMC")
    private String address;

    @DecimalMin(value = "50.0", message = "Height must be at least 50 cm.")
    @DecimalMax(value = "280.0", message = "Height must be at most 280 cm.")
    @Schema(description = "Height in centimeters", example = "172.5")
    private Double heightCm;

    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg.")
    @DecimalMax(value = "350.0", message = "Weight must be at most 350 kg.")
    @Schema(description = "Weight in kilograms", example = "65.0")
    private Double weightKg;

    @Size(max = 255, message = "Dietary preferences must not exceed 255 characters.")
    @Schema(description = "Comma-separated dietary preferences (VEGAN, VEGETARIAN, LACTO_OVO)", example = "VEGAN")
    private String dietaryPreferences;

    @Size(max = 255, message = "Allergies must not exceed 255 characters.")
    @Schema(description = "Comma-separated allergens (PEANUTS, GLUTEN, SOY, SESAME)", example = "PEANUTS, GLUTEN")
    private String allergies;

    @Min(value = 500, message = "Daily calorie target must be at least 500 kcal.")
    @Max(value = 10000, message = "Daily calorie target must be at most 10,000 kcal.")
    @Schema(description = "Daily target calories in kcal", example = "2000")
    private Integer dailyCalorieTarget;
}
