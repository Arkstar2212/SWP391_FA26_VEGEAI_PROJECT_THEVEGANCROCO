package com.vegeai.backend.modules.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu trích xuất công thức nấu ăn từ video")
public class VideoRecipeAiRequest {
    @Schema(description = "Tiêu đề video nấu ăn", example = "Cách làm cơm chiên mì gói chay")
    private String title;

    @Schema(description = "Mô tả / tóm tắt nội dung video", example = "Hướng dẫn chi tiết cách làm cơm chiên giòn từ mì gói chay và đậu phụ...")
    private String description;

    @Schema(description = "Đường dẫn video hoặc YouTube ID", example = "https://www.youtube.com/watch?v=ao_tEpz6niU")
    private String videoUrl;
}
