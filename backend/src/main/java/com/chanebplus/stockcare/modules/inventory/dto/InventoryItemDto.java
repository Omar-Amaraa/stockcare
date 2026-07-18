package com.chanebplus.stockcare.modules.inventory.dto;

import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemDto(
        UUID id,
        UUID pharmacyId,
        MedicationDto medication,
        int currentQuantity,
        int minimumQuantity,
        double averageDailyConsumption,
        Integer reorderThreshold,
        LocalDate expirationDate,
        boolean lowStock) {}
