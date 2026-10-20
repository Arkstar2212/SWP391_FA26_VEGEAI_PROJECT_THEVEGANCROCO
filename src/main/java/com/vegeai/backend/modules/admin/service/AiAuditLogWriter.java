package com.vegeai.backend.modules.admin.service;

import com.vegeai.backend.modules.admin.entity.AiAuditLog;
import com.vegeai.backend.modules.admin.repository.AiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiAuditLogWriter {
    private final AiAuditLogRepository logs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String operation, String model, String userType, String status, String outcome,
                     long latencyMs, String errorType) {
        AiAuditLog entry = new AiAuditLog();
        entry.setOperation(operation);
        entry.setModel(model);
        entry.setUserType(userType);
        entry.setStatus(status);
        entry.setOutcome(outcome);
        entry.setLatencyMs(latencyMs);
        entry.setErrorType(errorType);
        logs.save(entry);
    }
}
