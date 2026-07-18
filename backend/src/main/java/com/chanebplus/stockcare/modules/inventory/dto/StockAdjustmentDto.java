package com.chanebplus.stockcare.modules.inventory.dto;

import com.chanebplus.stockcare.modules.inventory.domain.AdjustmentReason;
import java.time.Instant;
import java.util.UUID;

public record StockAdjustmentDto(
        UUID id,
        int delta,
        int quantityBefore,
        int quantityAfter,
        AdjustmentReason reason,
        String note,
        Instant occurredAt) {}
