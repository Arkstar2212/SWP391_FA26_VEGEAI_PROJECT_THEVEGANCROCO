package com.example.swp391_fa26_vegeai_project.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    @NotBlank(message = "Message prompt cannot be blank")
    private String prompt;

    private Long userId; // Optional for guest trial
    private String conversationId;
}
