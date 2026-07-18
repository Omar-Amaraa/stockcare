package com.chanebplus.stockcare.modules.route.service;

import com.chanebplus.stockcare.common.error.BusinessRuleException;
import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationInput;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationResult;
import com.chanebplus.stockcare.modules.route.spi.RouteOptimizationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real route integration: calls the Python StockCare Routing Service (VRP-MILP / CBC) over HTTP.
 * Activated with {@code stockcare.models.route-optimization.mode=external}.
 *
 * <p>Priority coefficients are already computed upstream by the priority model and are passed
 * through as objective weights; the MILP derives each stop's SLA deadline from them and returns
 * the cost-optimal trajectory per vehicle.
 *
 * <p>Unlike the prediction integration, a failure here is <em>not</em> degraded silently: a
 * delivery planned from a broken optimizer would be operationally wrong, so transport errors and
 * infeasible instances surface to the depot as an explicit, explained failure.
 */
@Service
@ConditionalOnProperty(name = "stockcare.models.route-optimization.mode", havingValue = "external")
public class ExternalRouteOptimizationService implements RouteOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalRouteOptimizationService.class);

    private final ModelsProperties models;
    private final RestClient client;

    public ExternalRouteOptimizationService(ModelsProperties models) {
        this.models = models;
        String url = models.getRouteOptimization().getUrl();
        long timeout = models.getRouteOptimization().getTimeoutMs();
        RestClient.Builder builder = RestClient.builder();
        if (url != null && !url.isBlank()) {
            var settings = ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofMillis(Math.min(timeout, 3000)))
                    .withReadTimeout(Duration.ofMillis(timeout));
            builder = builder.baseUrl(url).requestFactory(ClientHttpRequestFactories.get(settings));
        }
        this.client = builder.build();
        log.info("ExternalRouteOptimizationService wired to {}", url);
    }

    @Override
    public String mode() {
        return "external";
    }

    @Override
    public RouteOptimizationResult optimize(RouteOptimizationInput input) {
        String url = models.getRouteOptimization().getUrl();
        if (url == null || url.isBlank()) {
            throw new BusinessRuleException(
                    "Route optimizer is in external mode but stockcare.models.route-optimization.url is not set.");
        }
        if (input.stops().isEmpty() || input.vehicles().isEmpty()) {
            throw new BusinessRuleException("Route optimization needs at least one stop and one vehicle.");
        }

        OptimizeRequest request = toRequest(input);
        OptimizeResponse response;
        try {
            response = client.post().uri("/optimize").body(request).retrieve().body(OptimizeResponse.class);
        } catch (RuntimeException ex) {
            log.error("Routing service call failed: {}", ex.getMessage());
            throw new BusinessRuleException(
                    "Route optimizer unreachable (" + ex.getMessage() + "). No delivery was planned.");
        }
        if (response == null) {
            throw new BusinessRuleException("Route optimizer returned an empty response.");
        }
        return toResult(response);
    }

    // ---- mapping ----

    private OptimizeRequest toRequest(RouteOptimizationInput input) {
        List<Stop> stops = new ArrayList<>();
        for (RouteOptimizationInput.Stop s : input.stops()) {
            stops.add(new Stop(
                    s.requestId().toString(),
                    s.pharmacyId().toString(),
                    s.latitude(),
                    s.longitude(),
                    s.priorityCoefficient(),
                    s.totalUnits(),
                    s.coldChain(),
                    s.lineCount(),
                    s.timeWindowStartMinute() == null ? null : s.timeWindowStartMinute().doubleValue(),
                    s.timeWindowEndMinute() == null ? null : s.timeWindowEndMinute().doubleValue()));
        }
        List<Vehicle> vehicles = new ArrayList<>();
        for (RouteOptimizationInput.Vehicle v : input.vehicles()) {
            vehicles.add(new Vehicle(v.vehicleId().toString(), v.capacityUnits(), v.refrigerated()));
        }
        Double maxMinutes = input.maxRouteMinutes() == null ? null : input.maxRouteMinutes().doubleValue();
        return new OptimizeRequest(input.depotId() == null ? null : input.depotId().toString(),
                input.depotLatitude(), input.depotLongitude(), stops, vehicles, maxMinutes, null);
    }

    private RouteOptimizationResult toResult(OptimizeResponse response) {
        List<RouteOptimizationResult.VehicleRoute> routes = new ArrayList<>();
        if (response.routes() != null) {
            for (Route r : response.routes()) {
                List<RouteOptimizationResult.OrderedStop> stops = new ArrayList<>();
                for (OrderedStop os : r.stops()) {
                    stops.add(new RouteOptimizationResult.OrderedStop(
                            os.sequence(),
                            UUID.fromString(os.requestId()),
                            UUID.fromString(os.pharmacyId()),
                            os.estimatedArrivalMinute()));
                }
                routes.add(new RouteOptimizationResult.VehicleRoute(
                        UUID.fromString(r.vehicleId()),
                        stops,
                        r.totalDistanceKm(),
                        r.totalDurationMinutes(),
                        r.capacityUsedUnits()));
            }
        }
        List<UUID> unfulfilled = new ArrayList<>();
        if (response.unfulfilledRequestIds() != null) {
            response.unfulfilledRequestIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(UUID::fromString)
                    .forEach(unfulfilled::add);
        }
        return new RouteOptimizationResult(response.status(), response.optimized(),
                response.objectiveValue(), routes, unfulfilled, response.note());
    }

    // ---- transport DTOs ----

    record Stop(String requestId, String pharmacyId, double latitude, double longitude,
                double priorityCoefficient, int totalUnits, boolean coldChain, Integer lineCount,
                Double timeWindowStartMinute, Double timeWindowEndMinute) {}

    record Vehicle(String vehicleId, int capacityUnits, boolean refrigerated) {}

    record OptimizeRequest(String depotId, double depotLatitude, double depotLongitude,
                           List<Stop> stops, List<Vehicle> vehicles, Double maxRouteMinutes,
                           Integer timeLimitSeconds) {}

    record OrderedStop(int sequence, String requestId, String pharmacyId, Double estimatedArrivalMinute,
                       Double slaDeadlineMinute, Double latenessMinutes, Double priorityCoefficient,
                       Boolean coldChain, Double units) {}

    record Route(String vehicleId, List<OrderedStop> stops, double totalDistanceKm,
                 double totalDurationMinutes, int capacityUsedUnits, Integer capacityUnits,
                 Boolean refrigerated) {}

    record OptimizeResponse(String status, boolean optimized, Double objectiveValue, List<Route> routes,
                            List<String> unfulfilledRequestIds, String note, String solverVersion,
                            Double totalLatenessMinutes) {}
}
