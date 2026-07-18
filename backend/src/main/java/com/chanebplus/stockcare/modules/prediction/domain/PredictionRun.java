package com.chanebplus.stockcare.modules.prediction.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-pharmacy prediction job state for the automated workflow. One row per pharmacy (upserted).
 * Carries the idempotency hash, correlation id, timestamps, retry count and model version so the
 * UI can render pending / processing / completed / failed / outdated states without manual actions.
 */
@Getter
@Setter
@Entity
@Table(name = "prediction_run")
public class PredictionRun extends BaseEntity {

    @Column(name = "pharmacy_id", nullable = false, unique = true)
    private UUID pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PredictionRunStatus status = PredictionRunStatus.PENDING;

    /** Hash of the inventory state this run was computed from (idempotency / duplicate prevention). */
    @Column(name = "input_hash", length = 64)
    private String inputHash;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "shortage_count", nullable = false)
    private int shortageCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
