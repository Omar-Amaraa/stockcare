package com.chanebplus.stockcare.modules.delivery.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** A planned/active delivery run for one vehicle serving one or more pharmacy stops. */
@Getter
@Setter
@Entity
@Table(name = "delivery")
public class Delivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status = DeliveryStatus.PLANNED;

    @Column(name = "reference", length = 32)
    private String reference;

    /** Marked true because routing is a mock, non-optimized plan in the MVP. */
    @Column(nullable = false)
    private boolean simulated = true;

    @Column(name = "optimizer_version", length = 64)
    private String optimizerVersion;

    @Column(name = "total_distance_km")
    private Double totalDistanceKm;

    @Column(name = "total_duration_minutes")
    private Double totalDurationMinutes;

    // Live tracking state
    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "current_stop_index")
    private Integer currentStopIndex;

    @Column(name = "progress")
    private double progress = 0.0;

    @Column(name = "eta_minutes")
    private Double etaMinutes;

    @Column(name = "planned_at")
    private Instant plannedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<RouteStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeliveryItem> items = new ArrayList<>();

    public void addStop(RouteStop stop) { stop.setDelivery(this); stops.add(stop); }
    public void addItem(DeliveryItem item) { item.setDelivery(this); items.add(item); }
}
