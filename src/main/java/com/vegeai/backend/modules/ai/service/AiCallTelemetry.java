package com.vegeai.backend.modules.ai.service;

import com.vegeai.backend.modules.ai.entity.AiCallLog;
import com.vegeai.backend.modules.ai.repository.AiCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiCallTelemetry {
    private final AiCallLogRepository repository;
    private final PlatformTransactionManager transactionManager;

    public void record(String model, String operation, long latencyMs, Exception failure) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setCreatedAt(Instant.now());
            entry.setModel(model);
            entry.setOperation(operation);
            entry.setStatus(failure == null ? "SUCCESS" : "FAILED");
            entry.setLatencyMs(latencyMs);
            if (failure instanceof RestClientResponseException response) {
                entry.setErrorType("HTTP_" + response.getStatusCode().value());
            } else if (failure != null) {
                entry.setErrorType("NETWORK_ERROR");
            }
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> repository.save(entry));
        } catch (Exception ex) {
            log.warn("Could not record AI call telemetry: {}", ex.getClass().getSimpleName());
        }
    }
}
