package com.chanebplus.stockcare.modules.medication.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** National medicine catalogue entry (aligned with the AMM registry fields used by the models). */
@Getter
@Setter
@Entity
@Table(name = "medication")
public class Medication extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** DCI / generic name. */
    @Column(name = "generic_name")
    private String genericName;

    @Column(length = 64)
    private String dosage;

    /** Pharmaceutical form (Forme / Présentation), e.g. TABLET, SYRUP, INJECTION. */
    @Column(name = "pharmaceutical_form", length = 64)
    private String pharmaceuticalForm;

    @Column(name = "package_size", length = 64)
    private String packageSize;

    /** Internal reference / SKU. */
    @Column(name = "sku", unique = true, length = 64)
    private String sku;

    @Column(length = 64)
    private String barcode;

    /** ATC therapeutic class used to separate chronic/vital from comfort use. */
    @Column(name = "atc_code", length = 16)
    private String atcCode;

    @Column(length = 64)
    private String category;

    /** Whether refrigerated transport is required (drives cold-chain routing priority). */
    @Column(name = "cold_chain", nullable = false)
    private boolean coldChain = false;

    /**
     * Reference clinical/logistical criticality in [0,1]. In production this comes from the
     * gradient-boosted criticality regressor; the MVP uses a seeded reference value.
     */
    @Column(name = "criticality_score")
    private Double criticalityScore;
}
