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
}
