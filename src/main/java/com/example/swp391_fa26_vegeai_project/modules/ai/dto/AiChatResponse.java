package com.example.swp391_fa26_vegeai_project.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String reply;
    private String modelUsed;
    private int tokensUsed;
    private boolean trialLimitReached;
    private String disclaimer;
}
