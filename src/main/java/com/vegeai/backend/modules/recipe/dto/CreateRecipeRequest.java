package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tạo công thức món ăn mới của Admin")
public class CreateRecipeRequest {

    @NotBlank(message = "Tiêu đề món ăn không được để trống")
    @Schema(description = "Tên món ăn", example = "Đậu phụ sốt cà chua tươi")
    private String title;

    @Schema(description = "Mô tả món ăn", example = "Món chay thanh đạm giàu dinh dưỡng, rất đưa cơm.")
    private String description;

    @Schema(description = "Thời gian chuẩn bị (phút)", example = "10")
    private Integer prepTimeMinutes;

    @Schema(description = "Thời gian nấu (phút)", example = "15")
    private Integer cookTimeMinutes;

    @Schema(description = "Số khẩu phần", example = "2")
    private Integer servings;

    @Schema(description = "Độ khó (EASY, MEDIUM, HARD)", example = "EASY")
    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.EASY;

    @Schema(description = "Năng lượng mỗi khẩu phần (kcal)", example = "240")
    private Integer calories;

    @Schema(description = "Lượng đạm (g)", example = "14.5")
    private Double proteinGrams;

    @Schema(description = "Carbs (g)", example = "18.2")
    private Double carbsGrams;

    @Schema(description = "Chất béo (g)", example = "8.1")
    private Double fatGrams;

    @Schema(description = "Chất xơ (g)", example = "4.2")
    private Double fiberGrams;

    @Schema(description = "URL ảnh minh họa món ăn")
    private String imageUrl;

    @Schema(description = "Trạng thái công thức (PUBLISHED, DRAFT)", example = "PUBLISHED")
    @Builder.Default
    private RecipeStatus status = RecipeStatus.PUBLISHED;

    @NotEmpty(message = "Danh sách nguyên liệu không được để trống")
    @Valid
    @Schema(description = "Danh sách nguyên liệu định lượng")
    private List<RecipeIngredientDto> ingredients;

    @NotEmpty(message = "Hướng dẫn các bước nấu không được để trống")
    @Schema(description = "Các bước nấu ăn theo thứ tự", example = "[\"Đậu phụ cắt miếng vuông vừa ăn, chiên vàng.\", \"Cà chua thái hạt lựu, xào nhuyễn tạo sốt.\", \"Cho đậu phụ vào đảo đều, nêm gia vị vừa ăn trong 5 phút.\"]")
    private List<String> instructions;

    @Schema(description = "Danh sách mã các thẻ tag gắn cho món ăn (VD: [\"VEGAN\", \"HIGH_PROTEIN\", \"QUICK_15\"])", example = "[\"VEGAN\", \"HIGH_PROTEIN\", \"QUICK_15\"]")
    private List<String> tagCodes;
}
