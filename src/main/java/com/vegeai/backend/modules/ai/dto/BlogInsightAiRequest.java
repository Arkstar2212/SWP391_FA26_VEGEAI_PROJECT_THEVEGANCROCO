package com.vegeai.backend.modules.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu AI phân tích luận điểm & tư vấn cá nhân hóa từ bài đăng cộng đồng")
public class BlogInsightAiRequest {

    @NotNull(message = "Vui lòng cung cấp mã bài viết cần phân tích.")
    @Schema(description = "ID bài đăng blog cần phân tích", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID postId;
}
