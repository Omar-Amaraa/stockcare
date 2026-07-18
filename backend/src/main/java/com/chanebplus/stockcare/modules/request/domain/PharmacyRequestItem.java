package com.chanebplus.stockcare.modules.request.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pharmacy_request_item")
public class PharmacyRequestItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PharmacyRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(length = 500)
    private String note;
}
