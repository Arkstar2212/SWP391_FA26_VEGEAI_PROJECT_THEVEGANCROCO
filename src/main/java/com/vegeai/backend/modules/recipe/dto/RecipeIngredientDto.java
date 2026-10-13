package com.vegeai.backend.modules.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin nguyên liệu trong công thức nấu ăn")
public class RecipeIngredientDto {

    @Schema(description = "ID nguyên liệu trong công thức (nếu có)")
    private UUID ingredientId;

    @NotBlank(message = "Tên nguyên liệu không được để trống")
    @Schema(description = "Tên nguyên liệu", example = "Đậu phụ non")
    private String name;

    @Schema(description = "Định lượng", example = "200.0")
    private Double amount;

    @Schema(description = "Đơn vị tính", example = "g")
    private String unit;

    @Schema(description = "Nguyên liệu tùy chọn (có hay không đều nấu được)", example = "false")
    @Builder.Default
    private boolean isOptional = false;

    @Schema(description = "Có phải thành phần dễ gây dị ứng không", example = "true")
    @Builder.Default
    private boolean isAllergen = false;

    @Schema(description = "Loại dị nguyên (SOY, GLUTEN, PEANUTS, MUSHROOM...)", example = "SOY")
    private String allergenType;
}
