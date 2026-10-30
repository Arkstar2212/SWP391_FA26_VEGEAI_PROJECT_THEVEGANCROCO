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

    @Pattern(regexp = "^$|^(?:(?:\\+84|84|0)(?:3|5|7|8|9)\\d{8})$", message = "Phone number must be a valid Vietnamese mobile number (+84 or 03/05/07/08/09 followed by 8 digits).")
    @Schema(description = "Phone number (e.g. 0912345678 or +84912345678)", example = "+84901234567")
    private String phoneNumber;

    @Schema(description = "Date of birth", example = "2000-01-15")
    private LocalDate dateOfBirth;

    @Size(max = 10, message = "Gender must be at most 10 characters.")
    @Schema(description = "Gender (e.g. MALE, FEMALE, OTHER)", example = "MALE")
    private String gender;

    @Size(max = 255, message = "Address must be at most 255 characters.")
    @Schema(description = "Residential address", example = "123 Nguyen Trai, District 1, HCMC")
    private String address;

    @DecimalMin(value = "100.0", message = "Height must be at least 100 cm.")
    @DecimalMax(value = "280.0", message = "Height must be at most 280 cm.")
    @Schema(description = "Height in centimeters", example = "172.5")
    private Double heightCm;

    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg.")
    @DecimalMax(value = "350.0", message = "Weight must be at most 350 kg.")
    @Schema(description = "Weight in kilograms", example = "65.0")
    private Double weightKg;

    @Size(max = 255, message = "Dietary preferences must not exceed 255 characters.")
    @Schema(
        description = "Dietary preference tag. Options: VEGAN (Thuần chay 100%), VEGETARIAN (Ăn chay chung), LACTO_OVO (Chay có trứng & sữa), LACTO (Chay có sữa), OVO (Chay có trứng), PESCATARIAN (Chay kèm hải sản), FLEXITARIAN (Bán chay), RAW_VEGAN (Thuần chay thô)",
        example = "VEGAN",
        allowableValues = {"VEGAN", "VEGETARIAN", "LACTO_OVO", "LACTO", "OVO", "PESCATARIAN", "FLEXITARIAN", "RAW_VEGAN"}
    )
    private String dietaryPreferences;

    @Size(max = 255, message = "Allergies must not exceed 255 characters.")
    @Schema(
        description = "Known food allergies comma-separated. Common options: NONE, PEANUTS (Đậu phộng), TREE_NUTS (Hạt dinh dưỡng), SOY (Đậu nành/đậu phụ), GLUTEN (Lúa mì), DAIRY (Sữa), EGGS (Trứng), SESAME (Mè/Vừng), MUSHROOMS (Nấm), CORN (Bắp/Ngô)",
        example = "PEANUTS, GLUTEN"
    )
    private String allergies;

    @Min(value = 500, message = "Daily calorie target must be at least 500 kcal.")
    @Max(value = 10000, message = "Daily calorie target must be at most 10,000 kcal.")
    @Schema(description = "Daily target calories in kcal", example = "2000")
    private Integer dailyCalorieTarget;
}
