package com.chanebplus.stockcare.modules.delivery.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A recorded GPS-like position for a delivery (simulated in the MVP). */
@Getter
@Setter
@Entity
@Table(name = "tracking_position")
public class TrackingPosition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "stop_index")
    private Integer stopIndex;

    @Column(name = "eta_minutes")
    private Double etaMinutes;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
}
