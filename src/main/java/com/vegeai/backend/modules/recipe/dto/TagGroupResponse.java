package com.vegeai.backend.modules.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Cây danh mục thẻ phân loại món chay đa tầng (Faceted Vegetarian Filter Explorer)")
public class TagGroupResponse {

    @Schema(description = "Trường phái ăn chay (DIETARY_TYPE): Thuần chay, Trứng, Sữa, Trứng sữa, Không ngũ vị tân, Chay thô")
    private List<TagDto> dietaryType;

    @Schema(description = "Phương pháp chế biến (COOKING_METHOD): Món kho, Món xào, Món chiên, Món hấp/luộc...")
    private List<TagDto> cookingMethod;

    @Schema(description = "Loại món ăn (DISH_CATEGORY): Món canh/Soup, Lẩu chay, Gỏi/Salad, Bún/Phở/Mì...")
    private List<TagDto> dishCategory;

    @Schema(description = "Mục tiêu dinh dưỡng (NUTRITION_GOAL): Giàu đạm, Giảm cân, Bổ máu, Tốt tim mạch, Tiểu đường Low-GI...")
    private List<TagDto> nutritionGoal;

    @Schema(description = "Thời gian và hoàn cảnh nấu (OCCASION): Nhanh < 15 phút, Dưới 30 phút, Nấu 1 nồi One-Pot, Tiết kiệm, Meal Prep...")
    private List<TagDto> occasion;

    @Schema(description = "Dị ứng và thành phần cần loại trừ (DIETARY_RESTRICTION): Không đậu nành, Không gluten, Không hạt, Không nấm...")
    private List<TagDto> dietaryRestriction;
}

