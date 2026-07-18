package com.chanebplus.stockcare.modules.priority.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Persisted priority coefficient for a request, including the contributing factors (JSON). */
@Getter
@Setter
@Entity
@Table(name = "priority_result")
public class PriorityResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private PharmacyRequest request;

    @Column(nullable = false)
    private double coefficient;

    /** Individual weighted factors serialised as JSON for transparency. */
    @Column(name = "factors_json", length = 2000)
    private String factorsJson;

    @Column(length = 1000)
    private String explanation;

    @Column(name = "calculation_version", length = 64)
    private String calculationVersion;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
