package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAuditLogEntry {

    private final String logId;
    private final Instant timestamp;
    private final String model;
    private final String operation;
    private final long latencyMs;
    private final String userType;
    private final String status;
    private final String errorType;
}
