package com.vegeai.backend.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Công thức nấu ăn chi tiết cá nhân hóa do AI tạo ra")
public class GeneratedRecipeAiResponse {

    @Schema(description = "ID công thức đã lưu vào sổ tay cá nhân (nếu đã lưu)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @Schema(description = "Tên món ăn", example = "Canh cải ngọt nấu nấm bào ngư thanh đạm")
    private String title;

    @Schema(description = "Mô tả ngắn gọn về món ăn và giá trị dinh dưỡng", example = "Món canh thanh ngọt tự nhiên, giàu chất xơ và đạm thực vật từ nấm.")
    private String description;

    @Schema(description = "Thời gian nấu ước tính (phút)", example = "20")
    private Integer cookTimeMinutes;

    @Schema(description = "Độ khó thực hiện: EASY, MEDIUM, HARD", example = "EASY")
    private String difficulty;

    @Schema(description = "Lượng calo ước tính mỗi khẩu phần (kcal)", example = "120")
    private Integer estimatedCalories;

    @Schema(description = "Trường phái ẩm thực chay", example = "VEGAN")
    private String dietaryType;

    @Schema(description = "Danh sách nguyên liệu và định lượng chi tiết")
    private List<String> ingredients;

    @Schema(description = "Các bước hướng dẫn chế biến chi tiết")
    private List<String> instructions;

    @Schema(description = "Lưu ý dinh dưỡng, mẹo nấu ngon hoặc lưu ý an toàn dị ứng")
    private List<String> safetyNotes;

    @Schema(description = "Thời điểm tạo công thức")
    private Instant createdAt;

    @Schema(description = "Cờ yêu cầu thiết lập hồ sơ (false nếu đã hoàn tất)")
    @JsonProperty("requiresProfileSetup")
    private boolean requiresProfileSetup;
}
