package com.chanebplus.stockcare.modules.pharmacy.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A pharmacy. Associated to one or more depots via the depot_pharmacy link table. */
@Getter
@Setter
@Entity
@Table(name = "pharmacy")
public class Pharmacy extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "license_number", length = 64)
    private String licenseNumber;

    @Column(name = "address_line")
    private String addressLine;

    /** Tunisian governorate / region (drives the future regional demand model). */
    @Column(length = 64)
    private String region;

    @Column(length = 64)
    private String city;

    private Double latitude;

    private Double longitude;

    @Column(length = 32)
    private String phone;
}
