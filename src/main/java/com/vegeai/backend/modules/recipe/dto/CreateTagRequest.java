package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.TagGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo thẻ tag phân loại mới (Admin)")
public class CreateTagRequest {

    @NotBlank(message = "Mã code thẻ không được để trống")
    @Pattern(regexp = "^[A-Z0-9_]{2,50}$", message = "Mã code phải viết hoa, gồm chữ cái, số và dấu gạch dưới (VD: HIGH_PROTEIN)")
    @Schema(description = "Mã code viết hoa", example = "HIGH_PROTEIN")
    private String code;

    @NotBlank(message = "Tên hiển thị không được để trống")
    @Schema(description = "Tên hiển thị tiếng Việt", example = "Giàu đạm (High-Protein)")
    private String name;

    @NotNull(message = "Nhóm thẻ không được để trống")
    @Schema(description = "Nhóm thẻ (DIETARY_TYPE, COOKING_METHOD, DISH_CATEGORY, NUTRITION_GOAL, OCCASION, DIETARY_RESTRICTION)", example = "NUTRITION_GOAL")
    private TagGroup group;

    @Schema(description = "Mô tả thẻ", example = "Cung cấp trên 15g đạm thực vật mỗi khẩu phần")
    private String description;
}
