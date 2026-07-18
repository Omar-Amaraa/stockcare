package com.chanebplus.stockcare.modules.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Depot creates a (mock) delivery plan from approved requests, assigning a vehicle and driver. */
public record DeliveryPlanRequest(
        @NotEmpty List<UUID> requestIds,
        @NotNull UUID vehicleId,
        UUID driverId) {}
