package com.chanebplus.stockcare.modules.route.dto;

import java.util.List;
import java.util.UUID;

/**
 * Generic input for a route optimizer, kept intentionally broad so a real MILP implementation can
 * consume it later. Coordinates are WGS84; quantities and priorities come from approved requests.
 */
public record RouteOptimizationInput(
        UUID depotId,
        double depotLatitude,
        double depotLongitude,
        List<Stop> stops,
        List<Vehicle> vehicles,
        Integer maxRouteMinutes) {

    public record Stop(
            UUID requestId,
            UUID pharmacyId,
            double latitude,
            double longitude,
            double priorityCoefficient,
            int totalUnits,
            boolean coldChain,
            Integer timeWindowStartMinute,
            Integer timeWindowEndMinute,
            /** Number of order lines at this stop; drives the unloading (service) time. */
            Integer lineCount) {

        /** Backwards-compatible factory for callers that do not track order lines. */
        public Stop(UUID requestId, UUID pharmacyId, double latitude, double longitude,
                    double priorityCoefficient, int totalUnits, boolean coldChain,
                    Integer timeWindowStartMinute, Integer timeWindowEndMinute) {
            this(requestId, pharmacyId, latitude, longitude, priorityCoefficient, totalUnits,
                    coldChain, timeWindowStartMinute, timeWindowEndMinute, null);
        }
    }

    public record Vehicle(
            UUID vehicleId,
            int capacityUnits,
            boolean refrigerated) {}
}
