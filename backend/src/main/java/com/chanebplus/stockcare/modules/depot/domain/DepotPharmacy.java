package com.chanebplus.stockcare.modules.depot.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Association between a depot and a pharmacy. Modelled as a link entity so the relationship can
 * grow to many-to-many later; in the MVP every pharmacy links to the single depot.
 */
@Getter
@Setter
@Entity
@Table(name = "depot_pharmacy",
        uniqueConstraints = @UniqueConstraint(columnNames = {"depot_id", "pharmacy_id"}))
public class DepotPharmacy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;
}
