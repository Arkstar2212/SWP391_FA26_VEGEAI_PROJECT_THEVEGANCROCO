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
@Schema(description = "Báo cáo tổng hợp đề xuất món ăn so khớp từ kho tủ lạnh cá nhân")
public class FridgeRecipeMatchResponse {

    @Schema(description = "Tổng số lượng công thức chuẩn được quét thẩm định", example = "25")
    private int totalRecipesEvaluated;

    @Schema(description = "Số món có thể nấu ngay lập tức (Đủ 100% nguyên liệu)", example = "3")
    private int canCookNowCount;

    @Schema(description = "Số món chỉ cần mua thêm 1-2 nguyên liệu (Khớp >= 60%)", example = "5")
    private int missingFewCount;

    @Schema(description = "Danh sách các món ăn được xếp theo tỷ lệ khớp giảm dần (100% lên trên đầu)")
    private List<FridgeRecipeMatchDto> matchedRecipes;
}
