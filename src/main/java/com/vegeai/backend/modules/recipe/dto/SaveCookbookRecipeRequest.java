package com.vegeai.backend.modules.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Yêu cầu lưu công thức vào sổ tay món ăn cá nhân (Cookbook)")
public class SaveCookbookRecipeRequest {

    @NotBlank(message = "Tên món ăn không được để trống.")
    @Size(max = 200, message = "Tên món ăn tối đa 200 ký tự.")
    @Schema(description = "Tên món ăn", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String title;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự.")
    @Schema(description = "Mô tả ngắn gọn về món ăn", example = "Món canh thanh ngọt, giàu chất xơ và đạm thực vật từ nấm.")
    private String description;

    @Schema(description = "Thời gian nấu ước tính (phút)", example = "20")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó thực hiện: EASY, MEDIUM, HARD", example = "EASY")
    private String difficulty;

    @Schema(description = "Lượng calo ước tính mỗi khẩu phần (kcal)", example = "120")
    private Integer estimatedCalories;

    @Schema(description = "Trường phái ẩm thực chay: VEGAN, VEGETARIAN, LACTO_OVO...", example = "VEGAN")
    private String dietaryType;

    @Schema(description = "Danh sách nguyên liệu và định lượng chi tiết")
    private List<String> ingredients;

    @Schema(description = "Các bước hướng dẫn nấu chi tiết")
    private List<String> instructions;

    @Schema(description = "Lưu ý an toàn, lưu ý dị ứng hoặc mẹo nấu ăn ngon")
    private List<String> safetyNotes;

    @Schema(description = "Nguồn tạo công thức (AI_GENERATED, FRIDGE_SCAN, AI_CHEF, MANUAL)", example = "AI_GENERATED")
    private String originSource;

    @Size(max = 1000, message = "Ghi chú khẩu vị tối đa 1000 ký tự.")
    @Schema(description = "Ghi chú kinh nghiệm nấu hoặc khẩu vị của bạn", example = "Nấu cho 2 người ăn, giảm một chút muối.")
    private String sourceNotes;

    @Schema(description = "ID công thức mẫu nếu lưu từ kho công thức chung", example = "null")
    private UUID originRecipeId;

    @Schema(description = "Link ảnh món ăn nếu có", example = "https://images.unsplash.com/...")
    private String imageUrl;
}
