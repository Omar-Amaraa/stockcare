package com.chanebplus.stockcare.modules.delivery.dto;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a fleet-wide planning run: the deliveries created, plus what the optimizer could not
 * place and why, so the depot can act on it instead of guessing.
 */
public record FleetPlanResult(
        String status,
        boolean optimized,
        Double objectiveValue,
        String note,
        List<DeliveryDto> deliveries,
        List<UUID> unfulfilledRequestIds) {}
