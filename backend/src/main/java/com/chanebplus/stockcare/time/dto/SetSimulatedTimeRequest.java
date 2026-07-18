package com.chanebplus.stockcare.time.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SetSimulatedTimeRequest(
        @NotNull Instant dateTime,
        boolean frozen) {}
