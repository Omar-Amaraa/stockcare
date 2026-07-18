package com.chanebplus.stockcare.modules.delivery.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vehicle")
public class Vehicle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "plate_number", length = 32)
    private String plateNumber;

    @Column(name = "capacity_units", nullable = false)
    private int capacityUnits;

    @Column(nullable = false)
    private boolean refrigerated = false;

    @Column(nullable = false)
    private boolean active = true;
}
