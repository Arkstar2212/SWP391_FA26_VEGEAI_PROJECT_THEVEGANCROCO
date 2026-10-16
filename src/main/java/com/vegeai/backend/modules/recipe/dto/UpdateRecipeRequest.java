package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu chỉnh sửa công thức món ăn của Admin")
public class UpdateRecipeRequest {

    @Schema(description = "Tên món ăn mới", example = "Đậu phụ sốt cà chua tươi đậm đà")
    private String title;

    @Schema(description = "Mô tả món ăn")
    private String description;

    @Schema(description = "Thời gian chuẩn bị (phút)")
    private Integer prepTimeMinutes;

    @Schema(description = "Thời gian nấu (phút)")
    private Integer cookTimeMinutes;

    @Schema(description = "Số khẩu phần")
    private Integer servings;

    @Schema(description = "Độ khó (EASY, MEDIUM, HARD)")
    private DifficultyLevel difficulty;

    @Schema(description = "Năng lượng mỗi khẩu phần (kcal)")
    private Integer calories;

    @Schema(description = "Lượng đạm (g)")
    private Double proteinGrams;

    @Schema(description = "Carbs (g)")
    private Double carbsGrams;

    @Schema(description = "Chất béo (g)")
    private Double fatGrams;

    @Schema(description = "Chất xơ (g)")
    private Double fiberGrams;

    @Schema(description = "URL ảnh minh họa món ăn")
    private String imageUrl;

    @Schema(description = "Trạng thái công thức (PUBLISHED, DRAFT, ARCHIVED)")
    private RecipeStatus status;

    @Valid
    @Schema(description = "Danh sách nguyên liệu cập nhật mới")
    private List<RecipeIngredientDto> ingredients;

    @Schema(description = "Các bước hướng dẫn nấu ăn mới")
    private List<String> instructions;

    @Schema(description = "Danh sách mã các thẻ tag mới")
    private List<String> tagCodes;
    @jakarta.validation.constraints.Size(max = 10)
    private List<@jakarta.validation.constraints.NotNull UUID> videoIds;
}
