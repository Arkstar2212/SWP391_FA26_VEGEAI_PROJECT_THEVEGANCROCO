package com.vegeai.backend.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu thiết lập nhanh hồ sơ dinh dưỡng & sở thích ăn chay (Quick Profile Modal)")
public class QuickProfileRequest {

    @NotBlank(message = "Vui lòng chọn trường phái ăn chay của bạn.")
    @Size(max = 255, message = "Trường phái ăn chay tối đa 255 ký tự.")
    @Schema(
        description = "Trường phái ăn chay: VEGAN, VEGETARIAN, LACTO_OVO, LACTO, OVO, PESCATARIAN, FLEXITARIAN, RAW_VEGAN",
        example = "VEGAN",
        allowableValues = {"VEGAN", "VEGETARIAN", "LACTO_OVO", "LACTO", "OVO", "PESCATARIAN", "FLEXITARIAN", "RAW_VEGAN"},
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String dietaryPreferences;

    @Size(max = 255, message = "Thông tin dị ứng tối đa 255 ký tự.")
    @Schema(
        description = "Danh sách dị ứng cách nhau bởi dấu phẩy (hoặc NONE). Ví dụ: PEANUTS, SOY, GLUTEN",
        example = "PEANUTS, GLUTEN"
    )
    private String allergies;

    @DecimalMin(value = "100.0", message = "Chiều cao tối thiểu 100 cm.")
    @DecimalMax(value = "280.0", message = "Chiều cao tối đa 280 cm.")
    @Schema(description = "Chiều cao (cm) tùy chọn để tính BMI", example = "172.5")
    private Double heightCm;

    @DecimalMin(value = "20.0", message = "Cân nặng tối thiểu 20 kg.")
    @DecimalMax(value = "350.0", message = "Cân nặng tối đa 350 kg.")
    @Schema(description = "Cân nặng (kg) tùy chọn để tính BMI", example = "65.0")
    private Double weightKg;

    @Min(value = 500, message = "Mục tiêu calo tối thiểu 500 kcal.")
    @Max(value = 10000, message = "Mục tiêu calo tối đa 10,000 kcal.")
    @Schema(description = "Mục tiêu calo hàng ngày (kcal)", example = "2000")
    private Integer dailyCalorieTarget;
}
