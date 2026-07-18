package com.chanebplus.stockcare.modules.request.dto;

import com.chanebplus.stockcare.modules.request.domain.Urgency;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Generate a draft request from a stored shortage prediction. Quantity defaults to the predicted gap. */
public record DraftFromPredictionDto(
        @NotNull UUID predictionId,
        Urgency urgency,
        Integer requestedQuantity,
        String notes,
        Integer affectedPatients) {}
