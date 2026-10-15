package com.vegeai.backend.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank(message = "Prompt message cannot be empty.")
    @Size(max = 2000, message = "Prompt must not exceed 2000 characters.")
    private String message;

    private String sessionId;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Màn hình người dùng đang đứng khi gọi AI: FRIDGE, RECIPE, NUTRITION, GENERAL", example = "FRIDGE")
    private String contextScreen;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Dữ liệu ngữ cảnh bổ sung kèm theo (ví dụ: danh sách nguyên liệu tủ lạnh)", example = "Cải ngọt, Cà chua, Đậu phụ")
    private String contextData;
}
