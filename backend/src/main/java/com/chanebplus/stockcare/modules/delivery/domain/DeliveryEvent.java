package com.chanebplus.stockcare.modules.delivery.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A timeline event in a delivery's lifecycle. */
@Getter
@Setter
@Entity
@Table(name = "delivery_event")
public class DeliveryEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryEventType type;

    @Column(length = 500)
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private Double latitude;

    private Double longitude;
}
