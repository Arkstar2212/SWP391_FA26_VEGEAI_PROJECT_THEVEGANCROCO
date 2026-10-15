package com.vegeai.backend.modules.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật công thức trong sổ tay cá nhân")
public class UpdateCookbookRecipeRequest {

    @Size(max = 200, message = "Tên món ăn tối đa 200 ký tự.")
    @Schema(description = "Tên món ăn", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự.")
    @Schema(description = "Mô tả ngắn gọn về món ăn")
    private String description;

    @Schema(description = "Thời gian nấu ước tính (phút)")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó thực hiện: EASY, MEDIUM, HARD")
    private String difficulty;

    @Schema(description = "Lượng calo ước tính mỗi khẩu phần (kcal)")
    private Integer estimatedCalories;

    @Schema(description = "Trường phái ẩm thực chay: VEGAN, VEGETARIAN, LACTO_OVO...")
    private String dietaryType;

    @Schema(description = "Danh sách nguyên liệu và định lượng chi tiết")
    private List<String> ingredients;

    @Schema(description = "Các bước hướng dẫn nấu chi tiết")
    private List<String> instructions;

    @Schema(description = "Lưu ý an toàn, lưu ý dị ứng hoặc mẹo nấu ăn ngon")
    private List<String> safetyNotes;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự.")
    @Schema(description = "Ghi chú kinh nghiệm nấu hoặc tùy chỉnh khẩu vị của bạn", example = "Lần sau nấu nhớ cho thêm gừng bớt mùi nấm.")
    private String sourceNotes;

    @Schema(description = "Link ảnh món ăn")
    private String imageUrl;
}
