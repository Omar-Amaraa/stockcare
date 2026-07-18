package com.chanebplus.stockcare.modules.request.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** A restock request from a pharmacy to its depot, containing one or more medication lines. */
@Getter
@Setter
@Entity
@Table(name = "pharmacy_request")
public class PharmacyRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status = RequestStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Urgency urgency = Urgency.NORMAL;

    @Column(length = 1000)
    private String notes;

    @Column(name = "internal_notes", length = 1000)
    private String internalNotes;

    /** Number of patients depending on this request (optional; feeds priority scoring). */
    @Column(name = "affected_patients")
    private Integer affectedPatients;

    /** Set when the request was drafted from a shortage prediction (traceability). */
    @Column(name = "source_prediction_id")
    private java.util.UUID sourcePredictionId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PharmacyRequestItem> items = new ArrayList<>();

    public void addItem(PharmacyRequestItem item) {
        item.setRequest(this);
        items.add(item);
    }
}
