package com.chanebplus.stockcare.integration.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a {@link ModelExecutionLog} entry for traceability of model calls. */
@Service
public class ModelExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ModelExecutionRecorder.class);

    private final ModelExecutionLogRepository repository;

    public ModelExecutionRecorder(ModelExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ModelType type, String mode, String version, ModelExecutionStatus status,
                       long durationMs, String inputSummary, String outputSummary, String error) {
        ModelExecutionLog entry = new ModelExecutionLog();
        entry.setModelType(type);
        entry.setMode(mode);
        entry.setModelVersion(version);
        entry.setCorrelationId(MDC.get("correlationId"));
        entry.setStatus(status);
        entry.setDurationMs(durationMs);
        entry.setInputSummary(truncate(inputSummary, 1000));
        entry.setOutputSummary(truncate(outputSummary, 1000));
        entry.setErrorMessage(truncate(error, 500));
        repository.save(entry);
        log.debug("Model {} [{}] {} in {}ms", type, mode, status, durationMs);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
