package com.vegeai.backend.modules.recipe.dto;

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
@Schema(description = "Chi tiết công thức trong Sổ tay món ăn cá nhân (Personal Cookbook)")
public class CookbookRecipeResponse {

    @Schema(description = "ID công thức sổ tay cá nhân", example = "e0b11234-5678-9abc-def0-123456789abc")
    private UUID id;

    @Schema(description = "ID người dùng sở hữu công thức")
    private UUID userId;

    @Schema(description = "Tên món ăn", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String title;

    @Schema(description = "Mô tả món ăn và giá trị dinh dưỡng")
    private String description;

    @Schema(description = "Thời gian nấu ước tính (phút)", example = "20")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó thực hiện: EASY, MEDIUM, HARD", example = "EASY")
    private String difficulty;

    @Schema(description = "Lượng calo ước tính mỗi khẩu phần (kcal)", example = "120")
    private Integer estimatedCalories;

    @Schema(description = "Trường phái ẩm thực chay", example = "VEGAN")
    private String dietaryType;

    @Schema(description = "Nguồn tạo công thức (AI_GENERATED, FRIDGE_SCAN, AI_CHEF, MANUAL)", example = "AI_GENERATED")
    private String originSource;

    @Schema(description = "Danh sách nguyên liệu và định lượng chi tiết")
    private List<String> ingredients;

    @Schema(description = "Các bước hướng dẫn chế biến chi tiết")
    private List<String> instructions;

    @Schema(description = "Lưu ý an toàn, dị ứng hoặc mẹo nấu ăn")
    private List<String> safetyNotes;

    @Schema(description = "Ghi chú kinh nghiệm nấu hoặc khẩu vị riêng của người dùng")
    private String sourceNotes;

    @Schema(description = "ID công thức mẫu gốc (nếu lưu từ kho công thức chung)")
    private UUID originRecipeId;

    @Schema(description = "Ảnh đại diện món ăn")
    private String imageUrl;

    @Schema(description = "Thời điểm lưu công thức vào sổ tay")
    private LocalDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private LocalDateTime updatedAt;
}
