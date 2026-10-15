package com.vegeai.backend.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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

    @Size(max = 128)
    private String sessionId;

    @Size(max = 10)
    private List<@NotNull @Valid ChatMessage> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        @NotBlank
        @Pattern(regexp = "user|model")
        private String role;
        @NotBlank
        @Size(max = 4000)
        private String text;
    }

    @io.swagger.v3.oas.annotations.media.Schema(description = "Màn hình người dùng đang đứng khi gọi AI: FRIDGE, RECIPE, NUTRITION, GENERAL", example = "FRIDGE")
    private String contextScreen;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Dữ liệu ngữ cảnh bổ sung kèm theo (ví dụ: danh sách nguyên liệu tủ lạnh)", example = "Cải ngọt, Cà chua, Đậu phụ")
    private String contextData;
}
