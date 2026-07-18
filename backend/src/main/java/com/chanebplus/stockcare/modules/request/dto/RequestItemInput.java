package com.chanebplus.stockcare.modules.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RequestItemInput(
        @NotNull UUID medicationId,
        @Positive int requestedQuantity,
        String note) {}
