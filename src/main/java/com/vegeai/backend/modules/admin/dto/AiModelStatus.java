package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiModelStatus {

    private final String modelName;
    private final String provider;
    private final String version;
    private final String status;      // e.g. "OPERATIONAL", "DEGRADED", "OFFLINE"
    private final int latencyMs;
    private final String rateLimitQuota;
}
