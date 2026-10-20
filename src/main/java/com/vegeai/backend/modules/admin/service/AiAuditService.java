package com.vegeai.backend.modules.admin.service;

import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAuditService {
    private final AiAuditLogWriter writer;

    public <T> T track(String operation, String model, String userType, AiOperation<T> action) throws Exception {
        return track(operation, model, userType, action, ignored -> null);
    }

    public <T> T track(String operation, String model, String userType, AiOperation<T> action,
                       Function<T, String> outcomeSelector) throws Exception {
        long startedAt = System.nanoTime();
        String status = "SUCCESS";
        String errorType = null;
        String outcome = null;
        try {
            T result = action.run();
            outcome = outcomeSelector.apply(result);
            return result;
        } catch (Exception exception) {
            status = "FAILED";
            errorType = errorType(exception);
            throw exception;
        } finally {
            long latencyMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
            try {
                writer.save(operation, model, userType, status, outcome, latencyMs, errorType);
            } catch (RuntimeException exception) {
                // An audit outage must not hide the AI result, but is visible to operators in server logs.
                log.error("[AI-AUDIT] Could not persist {} telemetry: {}", operation, exception.getMessage(), exception);
            }
        }
    }

    private static String errorType(Exception exception) {
        if (exception instanceof RestClientResponseException response) {
            return "HTTP_" + response.getStatusCode().value();
        }
        return NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName();
    }

    @FunctionalInterface
    public interface AiOperation<T> {
        T run() throws Exception;
    }
}
