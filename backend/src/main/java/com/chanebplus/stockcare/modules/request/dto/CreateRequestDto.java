package com.chanebplus.stockcare.modules.request.dto;

import com.chanebplus.stockcare.modules.request.domain.Urgency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateRequestDto(
        @NotNull Urgency urgency,
        String notes,
        Integer affectedPatients,
        @NotEmpty @Valid List<RequestItemInput> items) {}
