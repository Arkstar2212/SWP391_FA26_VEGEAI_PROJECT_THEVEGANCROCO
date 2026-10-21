package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiOperationStats {

    private final String operation;   // e.g. "NUTRITION_CHAT", "FRIDGE_SCAN"
    private final long totalRequests;
    private final long failedRequests;
    private final Double averageLatencyMs;
}
