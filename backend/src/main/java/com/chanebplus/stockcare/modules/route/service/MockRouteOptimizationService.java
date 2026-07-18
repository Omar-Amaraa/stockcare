package com.chanebplus.stockcare.modules.route.service;

import com.chanebplus.stockcare.common.geo.GeoUtils;
import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationInput;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationResult;
import com.chanebplus.stockcare.modules.route.spi.RouteOptimizationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * NON-OPTIMIZED mock router. Sorts stops by priority, packs them into vehicles by capacity
 * (respecting cold-chain), then orders each vehicle's stops greedily by nearest-neighbour from the
 * depot and computes approximate haversine distances/durations. Clearly labelled as test output; a
 * real MILP optimizer replaces this behind {@link RouteOptimizationService}.
 */
@Service
@ConditionalOnProperty(name = "stockcare.models.route-optimization.mode", havingValue = "mock", matchIfMissing = true)
public class MockRouteOptimizationService implements RouteOptimizationService {

    private static final double AVG_SPEED_KMH = 40.0;
    private static final double SERVICE_MINUTES_PER_STOP = 8.0;

    private final ModelsProperties models;

    public MockRouteOptimizationService(ModelsProperties models) {
        this.models = models;
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public RouteOptimizationResult optimize(RouteOptimizationInput input) {
        List<RouteOptimizationInput.Stop> pending = new ArrayList<>(input.stops());
        pending.sort(Comparator.comparingDouble(RouteOptimizationInput.Stop::priorityCoefficient).reversed());

        List<RouteOptimizationResult.VehicleRoute> routes = new ArrayList<>();
        List<java.util.UUID> unfulfilled = new ArrayList<>();
        double totalObjective = 0;

        List<RouteOptimizationInput.Vehicle> vehicles = input.vehicles();
        int vIndex = 0;
        List<RouteOptimizationInput.Stop> remaining = new ArrayList<>(pending);

        while (vIndex < vehicles.size() && !remaining.isEmpty()) {
            RouteOptimizationInput.Vehicle vehicle = vehicles.get(vIndex);
            int capacity = vehicle.capacityUnits();
            List<RouteOptimizationInput.Stop> assigned = new ArrayList<>();
            int used = 0;
            for (var it = remaining.iterator(); it.hasNext(); ) {
                RouteOptimizationInput.Stop s = it.next();
                if (s.coldChain() && !vehicle.refrigerated()) {
                    continue;
                }
                if (used + s.totalUnits() <= capacity) {
                    assigned.add(s);
                    used += s.totalUnits();
                    it.remove();
                }
            }
            if (!assigned.isEmpty()) {
                RouteOptimizationResult.VehicleRoute route =
                        buildRoute(input, vehicle, assigned, used);
                routes.add(route);
                totalObjective += route.totalDistanceKm();
            }
            vIndex++;
        }
        remaining.forEach(s -> unfulfilled.add(s.requestId()));

        return new RouteOptimizationResult("MOCK_OK", false, totalObjective, routes, unfulfilled,
                "Non-optimized test output from mock router (" + models.getRouteOptimization().getVersion() + ").");
    }

    private RouteOptimizationResult.VehicleRoute buildRoute(RouteOptimizationInput input,
                                                            RouteOptimizationInput.Vehicle vehicle,
                                                            List<RouteOptimizationInput.Stop> stops, int used) {
        // Greedy nearest-neighbour ordering starting from the depot.
        List<RouteOptimizationInput.Stop> ordered = new ArrayList<>();
        List<RouteOptimizationInput.Stop> pool = new ArrayList<>(stops);
        double curLat = input.depotLatitude();
        double curLon = input.depotLongitude();
        double totalKm = 0;
        double totalMin = 0;
        List<RouteOptimizationResult.OrderedStop> orderedStops = new ArrayList<>();
        int seq = 1;
        while (!pool.isEmpty()) {
            RouteOptimizationInput.Stop nearest = null;
            double best = Double.MAX_VALUE;
            for (RouteOptimizationInput.Stop s : pool) {
                double d = GeoUtils.distanceKm(curLat, curLon, s.latitude(), s.longitude());
                if (d < best) { best = d; nearest = s; }
            }
            totalKm += best;
            totalMin += best / AVG_SPEED_KMH * 60.0 + SERVICE_MINUTES_PER_STOP;
            orderedStops.add(new RouteOptimizationResult.OrderedStop(seq++, nearest.requestId(),
                    nearest.pharmacyId(), Math.round(totalMin * 10.0) / 10.0));
            ordered.add(nearest);
            pool.remove(nearest);
            curLat = nearest.latitude();
            curLon = nearest.longitude();
        }
        return new RouteOptimizationResult.VehicleRoute(vehicle.vehicleId(), orderedStops,
                Math.round(totalKm * 100.0) / 100.0, Math.round(totalMin * 10.0) / 10.0, used);
    }
}
