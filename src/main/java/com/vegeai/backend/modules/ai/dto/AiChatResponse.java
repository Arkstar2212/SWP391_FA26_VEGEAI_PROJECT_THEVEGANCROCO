package com.vegeai.backend.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String reply;
    private String sessionId;
    private List<String> recommendedRecipes;
    private Instant timestamp;
    private boolean isGuestTrial;
    private Integer remainingGuestQuota;
}
