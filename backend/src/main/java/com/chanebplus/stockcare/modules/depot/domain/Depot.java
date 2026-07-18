package com.chanebplus.stockcare.modules.depot.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A distribution depot. The MVP runs with a single depot serving many pharmacies. */
@Getter
@Setter
@Entity
@Table(name = "depot")
public class Depot extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "address_line")
    private String addressLine;

    /** Tunisian governorate / region. */
    @Column(length = 64)
    private String region;

    @Column(length = 64)
    private String city;

    private Double latitude;

    private Double longitude;

    @Column(length = 32)
    private String phone;
}
