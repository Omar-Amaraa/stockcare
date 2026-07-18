package com.chanebplus.stockcare.modules.request.dto;

import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestDto(
        UUID id,
        UUID pharmacyId,
        String pharmacyName,
        UUID depotId,
        RequestStatus status,
        Urgency urgency,
        String notes,
        String internalNotes,
        Integer affectedPatients,
        UUID sourcePredictionId,
        Instant submittedAt,
        Instant createdAt,
        List<RequestItemDto> items,
        PriorityResultDto priority) {}
