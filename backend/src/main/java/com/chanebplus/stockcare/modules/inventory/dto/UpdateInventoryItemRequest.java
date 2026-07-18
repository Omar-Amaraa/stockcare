package com.chanebplus.stockcare.modules.inventory.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record UpdateInventoryItemRequest(
        @PositiveOrZero int minimumQuantity,
        @PositiveOrZero double averageDailyConsumption,
        Integer reorderThreshold,
        LocalDate expirationDate) {}
