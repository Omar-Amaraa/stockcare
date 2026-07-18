package com.chanebplus.stockcare.modules.request.dto;

import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusDto(@NotNull RequestStatus status) {}
