package com.chanebplus.stockcare.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInventoryItemRequest(
        @NotNull UUID medicationId,
        @PositiveOrZero int currentQuantity,
        @PositiveOrZero int minimumQuantity,
        @PositiveOrZero double averageDailyConsumption,
        Integer reorderThreshold,
        LocalDate expirationDate) {}
