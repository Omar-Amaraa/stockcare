package com.chanebplus.stockcare.modules.prediction.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Persisted shortage prediction. Always labelled with model version and the simulated flag. */
@Getter
@Setter
@Entity
@Table(name = "prediction_result")
public class PredictionResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "predicted_shortage_date")
    private LocalDate predictedShortageDate;

    @Column(name = "estimated_remaining_days")
    private Integer estimatedRemainingDays;

    @Column(name = "predicted_missing_quantity", nullable = false)
    private int predictedMissingQuantity;

    /** Nullable for now; the rule-based mock does not emit a calibrated confidence. */
    @Column(name = "confidence")
    private Double confidence;

    @Column(length = 1000)
    private String reason;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "prediction_time", nullable = false)
    private Instant predictionTime;
}
