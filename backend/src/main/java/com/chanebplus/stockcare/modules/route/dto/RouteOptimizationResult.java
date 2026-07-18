package com.chanebplus.stockcare.modules.route.dto;

import java.util.List;
import java.util.UUID;

/** Generic optimizer output. Mock plans set {@code optimized=false} and label themselves as test output. */
public record RouteOptimizationResult(
        String status,
        boolean optimized,
        Double objectiveValue,
        List<VehicleRoute> routes,
        List<UUID> unfulfilledRequestIds,
        String note) {

    public record VehicleRoute(
            UUID vehicleId,
            List<OrderedStop> stops,
            double totalDistanceKm,
            double totalDurationMinutes,
            int capacityUsedUnits) {}

    public record OrderedStop(
            int sequence,
            UUID requestId,
            UUID pharmacyId,
            Double estimatedArrivalMinute) {}
}
