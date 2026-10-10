package com.vegeai.backend.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("isGuestTrial")
    private boolean isGuestTrial;

    private Integer remainingGuestQuota;
}
