package com.chanebplus.stockcare.modules.inventory.dto;

import com.chanebplus.stockcare.modules.inventory.domain.AdjustmentReason;
import jakarta.validation.constraints.NotNull;

/** Records a stock movement. {@code delta} may be negative (e.g. a sale). */
public record StockAdjustmentRequest(
        int delta,
        @NotNull AdjustmentReason reason,
        String note) {}
