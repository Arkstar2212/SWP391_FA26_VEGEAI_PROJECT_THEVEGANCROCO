package com.vegeai.backend.modules.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Thẻ tóm tắt công thức món ăn (hiển thị danh sách kết quả tìm kiếm & bộ lọc)")
public class RecipeSummaryDto {

    @Schema(description = "ID công thức", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID recipeId;

    @Schema(description = "Tiêu đề món ăn", example = "Đậu phụ sốt cà chua tươi")
    private String title;

    @Schema(description = "Đường dẫn SEO slug", example = "dau-phu-sot-ca-chua-tuoi")
    private String slug;

    @Schema(description = "Ảnh đại diện món ăn")
    private String imageUrl;

    @Schema(description = "Thời gian nấu (phút)", example = "20")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó (EASY, MEDIUM, HARD)", example = "EASY")
    private DifficultyLevel difficulty;

    @Schema(description = "Trạng thái công thức")
    private RecipeStatus status;

    @Schema(description = "Năng lượng mỗi khẩu phần (kcal)", example = "240")
    private Integer calories;

    @Schema(description = "Lượng đạm (g)", example = "14.5")
    private Double proteinGrams;

    @Schema(description = "Công thức đã được Admin kiểm duyệt", example = "true")
    @JsonProperty("isVerified")
    private boolean isVerified;

    @Schema(description = "Danh sách thẻ tags gắn liền với món ăn")
    private List<TagDto> tags;
}
