package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.MatchGroup;
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
@Schema(description = "Kết quả so khớp một công thức với nguyên liệu hiện có trong tủ lạnh của người dùng")
public class FridgeRecipeMatchDto {

    @Schema(description = "Thông tin tóm tắt công thức món ăn")
    private RecipeSummaryDto recipe;

    @Schema(description = "Tỷ lệ khớp nguyên liệu (0 - 100%)", example = "100")
    private int matchPercentage;

    @Schema(description = "Phân nhóm so khớp: CAN_COOK_NOW (Nấu ngay 100%), MISSING_FEW (Thiếu 1-2 món >=60%), INSUFFICIENT (<60%)", example = "CAN_COOK_NOW")
    private MatchGroup matchGroup;

    @Schema(description = "Tổng số nguyên liệu bắt buộc của món ăn", example = "3")
    private int totalMandatoryIngredients;

    @Schema(description = "Số nguyên liệu bạn đã có sẵn trong tủ", example = "3")
    private int matchedIngredientsCount;

    @Schema(description = "Danh sách nguyên liệu đã có sẵn trong tủ", example = "[\"Đậu phụ non\", \"Cà chua\"]")
    private List<String> availableInFridge;

    @Schema(description = "Danh sách nguyên liệu còn thiếu cần mua thêm", example = "[\"Hành hoa\"]")
    private List<String> missingIngredients;

    @Schema(description = "Lời khuyên từ hệ thống", example = "Bạn có đủ 100% nguyên liệu, có thể bắt tay vào nấu ngay!")
    private String recommendation;
}
