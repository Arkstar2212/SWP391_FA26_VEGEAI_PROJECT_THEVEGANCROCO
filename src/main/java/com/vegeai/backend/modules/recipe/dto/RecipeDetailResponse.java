package com.vegeai.backend.modules.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chi tiết toàn diện công thức món ăn (Nguyên liệu, Dinh dưỡng, Các bước nấu, Tags)")
public class RecipeDetailResponse {

    @Schema(description = "ID công thức", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID recipeId;

    @Schema(description = "Tiêu đề món ăn", example = "Đậu phụ sốt cà chua tươi")
    private String title;

    @Schema(description = "Đường dẫn SEO slug", example = "dau-phu-sot-ca-chua-tuoi")
    private String slug;

    @Schema(description = "Mô tả tổng quan món ăn", example = "Món ăn thanh đạm, giàu đạm thực vật, dễ nấu trong 20 phút.")
    private String description;

    @Schema(description = "Thời gian chuẩn bị (phút)", example = "10")
    private Integer prepTimeMinutes;

    @Schema(description = "Thời gian nấu (phút)", example = "15")
    private Integer cookTimeMinutes;

    @Schema(description = "Số khẩu phần", example = "2")
    private Integer servings;

    @Schema(description = "Độ khó (EASY, MEDIUM, HARD)", example = "EASY")
    private DifficultyLevel difficulty;

    @Schema(description = "Năng lượng mỗi khẩu phần (kcal)", example = "240")
    private Integer calories;

    @Schema(description = "Đạm (g)", example = "14.5")
    private Double proteinGrams;

    @Schema(description = "Carbs (g)", example = "18.2")
    private Double carbsGrams;

    @Schema(description = "Chất béo (g)", example = "8.1")
    private Double fatGrams;

    @Schema(description = "Chất xơ (g)", example = "4.2")
    private Double fiberGrams;

    @Schema(description = "Ảnh đại diện món ăn")
    private String imageUrl;

    @Schema(description = "Công thức đã được Admin kiểm duyệt", example = "true")
    @JsonProperty("isVerified")
    private boolean isVerified;

    @Schema(description = "Trạng thái công thức", example = "PUBLISHED")
    private RecipeStatus status;

    @Schema(description = "Danh sách các nguyên liệu chi tiết")
    private List<RecipeIngredientDto> ingredients;

    @Schema(description = "Các bước hướng dẫn nấu ăn chi tiết theo thứ tự")
    private List<String> instructions;

    @Schema(description = "Danh sách thẻ tags phân loại")
    private List<TagDto> tags;

    @Schema(description = "Thời điểm tạo")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật")
    private LocalDateTime updatedAt;
}
