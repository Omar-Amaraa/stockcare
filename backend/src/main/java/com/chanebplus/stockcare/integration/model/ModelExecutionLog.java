package com.chanebplus.stockcare.integration.model;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Debug/audit trail for every model invocation (mock or external). Stores a non-sensitive summary
 * of inputs and outputs, the mode, version, correlation id, status and duration.
 */
@Getter
@Setter
@Entity
@Table(name = "model_execution_log")
public class ModelExecutionLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "model_type", nullable = false, length = 32)
    private ModelType modelType;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModelExecutionStatus status;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "input_summary", length = 1000)
    private String inputSummary;

    @Column(name = "output_summary", length = 1000)
    private String outputSummary;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
