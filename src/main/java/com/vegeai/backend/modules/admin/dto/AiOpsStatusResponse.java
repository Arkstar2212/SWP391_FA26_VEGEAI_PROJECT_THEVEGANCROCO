package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiOpsStatusResponse {

    private final String systemHealth;
    private final Double successRatePercentage;
    private final Double averageLatencyMs;
    private final long totalAiRequests;
    private final long failedAiRequests;
    private final Instant generatedAt;
    private final List<AiModelStatus> models;
}
