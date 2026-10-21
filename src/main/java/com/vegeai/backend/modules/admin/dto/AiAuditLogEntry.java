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
<<<<<<< HEAD
    private final String errorType;
=======
    private final String outcome;
    private final String errorType;
    private final String promptSummary;
>>>>>>> 6a63815723b213a298a5c7dd70a9dbc7665793ab
}
