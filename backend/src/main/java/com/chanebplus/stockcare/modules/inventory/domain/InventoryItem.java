package com.chanebplus.stockcare.modules.inventory.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A pharmacy's stock record for a single medication (manually managed in the MVP). */
@Getter
@Setter
@Entity
@Table(name = "inventory_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pharmacy_id", "medication_id"}))
public class InventoryItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "current_quantity", nullable = false)
    private int currentQuantity;

    /** Minimum desired stock (safety-stock buffer S in the shortage-gap formula). */
    @Column(name = "minimum_quantity", nullable = false)
    private int minimumQuantity;

    @Column(name = "avg_daily_consumption", nullable = false)
    private double averageDailyConsumption;

    /** Optional reorder threshold; defaults to minimumQuantity when null. */
    @Column(name = "reorder_threshold")
    private Integer reorderThreshold;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;
}
