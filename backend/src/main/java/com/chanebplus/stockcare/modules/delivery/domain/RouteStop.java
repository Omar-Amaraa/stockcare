package com.chanebplus.stockcare.modules.delivery.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** An ordered stop on a delivery's route (the delivery route is the ordered set of these stops). */
@Getter
@Setter
@Entity
@Table(name = "route_stop")
public class RouteStop extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(nullable = false)
    private int sequence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @Column(name = "request_id")
    private java.util.UUID requestId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RouteStopStatus status = RouteStopStatus.PENDING;

    @Column(name = "estimated_arrival_minute")
    private Double estimatedArrivalMinute;

    @Column(name = "distance_from_prev_km")
    private Double distanceFromPrevKm;

    @Column(name = "arrived_at")
    private Instant arrivedAt;
}
