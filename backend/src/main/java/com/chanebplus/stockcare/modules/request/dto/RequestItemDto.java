package com.chanebplus.stockcare.modules.request.dto;

import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;
import java.util.UUID;

public record RequestItemDto(
        UUID id,
        MedicationDto medication,
        int requestedQuantity,
        String note) {}
