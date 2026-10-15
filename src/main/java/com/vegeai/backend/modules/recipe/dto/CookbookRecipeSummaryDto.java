package com.vegeai.backend.modules.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tóm tắt công thức trong danh sách Sổ tay món ăn cá nhân (Personal Cookbook)")
public class CookbookRecipeSummaryDto {

    @Schema(description = "ID công thức trong sổ tay cá nhân", example = "e0b11234-5678-9abc-def0-123456789abc")
    private UUID id;

    @Schema(description = "Tên món ăn", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String title;

    @Schema(description = "Mô tả ngắn gọn về món ăn")
    private String description;

    @Schema(description = "Thời gian nấu ước tính (phút)", example = "20")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó thực hiện: EASY, MEDIUM, HARD", example = "EASY")
    private String difficulty;

    @Schema(description = "Lượng calo ước tính mỗi khẩu phần (kcal)", example = "120")
    private Integer estimatedCalories;

    @Schema(description = "Trường phái ẩm thực chay", example = "VEGAN")
    private String dietaryType;

    @Schema(description = "Nguồn tạo công thức", example = "AI_GENERATED")
    private String originSource;

    @Schema(description = "Số lượng nguyên liệu", example = "4")
    private int ingredientCount;

    @Schema(description = "Số bước chế biến", example = "5")
    private int stepCount;

    @Schema(description = "Ảnh đại diện món ăn")
    private String imageUrl;

    @Schema(description = "Thời điểm lưu vào sổ tay")
    private LocalDateTime createdAt;
}
