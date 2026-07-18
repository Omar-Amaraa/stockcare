package com.chanebplus.stockcare.modules.inventory.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Immutable history entry recording a change to an inventory item's quantity. */
@Getter
@Setter
@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(nullable = false)
    private int delta;

    @Column(name = "quantity_before", nullable = false)
    private int quantityBefore;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdjustmentReason reason;

    @Column(length = 500)
    private String note;

    /** Effective time from the application clock (may be simulated). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
