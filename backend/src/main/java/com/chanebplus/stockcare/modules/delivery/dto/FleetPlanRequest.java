package com.chanebplus.stockcare.modules.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Depot plans a whole wave of approved requests across the fleet in one solve.
 *
 * <p>The optimizer decides which vehicle serves which pharmacy and in what order; the backend then
 * materialises one {@code Delivery} per returned route. Leaving {@code vehicleIds} empty uses every
 * active vehicle of the depot. Drivers are optional and assigned to routes in the given order.
 */
public record FleetPlanRequest(
        @NotEmpty List<UUID> requestIds,
        List<UUID> vehicleIds,
        List<UUID> driverIds,
        Integer maxRouteMinutes) {

    public List<UUID> vehicleIdsOrEmpty() {
        return vehicleIds == null ? List.of() : vehicleIds;
    }

    public List<UUID> driverIdsOrEmpty() {
        return driverIds == null ? List.of() : driverIds;
    }
}
