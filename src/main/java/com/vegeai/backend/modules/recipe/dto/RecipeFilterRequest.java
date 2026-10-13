package com.vegeai.backend.modules.recipe.dto;

import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.InclusionMode;
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
@Schema(description = "Yêu cầu lọc công thức món chay đa tiêu chí (Faceted Tag Filter with AND/OR & Exclusion Logic)")
public class RecipeFilterRequest {

    @Schema(description = "Từ khóa tìm kiếm theo tên hoặc mô tả món ăn", example = "đậu phụ")
    private String keyword;

    @Schema(description = "Danh sách các mã thẻ tag muốn chọn (Inclusion Tags)", example = "[\"VEGAN\", \"HIGH_PROTEIN\", \"QUICK_15\"]")
    private List<String> includeTagCodes;

    @Schema(description = "Logic kết hợp các thẻ đã chọn: AND (phải có đủ tất cả thẻ) hoặc OR (có ít nhất 1 thẻ). Mặc định: AND", example = "AND")
    @Builder.Default
    private InclusionMode inclusionMode = InclusionMode.AND;

    @Schema(description = "Danh sách các mã thẻ tag TUYỆT ĐỐI LOẠI TRỪ (Exclusion Tags, ví dụ: dị ứng Gluten, kiêng Đậu nành, kiêng Cay)", example = "[\"GLUTEN\", \"SPICY\"]")
    private List<String> excludeTagCodes;

    @Schema(description = "Thời gian nấu tối đa (phút)", example = "30")
    private Integer maxCookTime;

    @Schema(description = "Mức năng lượng tối đa mỗi khẩu phần (kcal)", example = "400")
    private Integer maxCalories;

    @Schema(description = "Lượng đạm tối thiểu mỗi khẩu phần (g)", example = "10.0")
    private Double minProtein;

    @Schema(description = "Độ khó (EASY, MEDIUM, HARD)")
    private DifficultyLevel difficulty;

    @Schema(description = "Tiêu chí sắp xếp: newest (mới nhất), cookTime (thời gian nấu), calories (calo), protein (đạm). Mặc định: newest", example = "newest")
    @Builder.Default
    private String sortBy = "newest";

    @Schema(description = "Hướng sắp xếp: asc (tăng dần) hoặc desc (giảm dần). Mặc định: desc", example = "desc")
    @Builder.Default
    private String sortDirection = "desc";

    @Schema(description = "Số trang (bắt đầu từ 0). Mặc định: 0", example = "0")
    @Builder.Default
    private int page = 0;

    @Schema(description = "Số lượng bản ghi mỗi trang. Mặc định: 12", example = "12")
    @Builder.Default
    private int size = 12;
}
