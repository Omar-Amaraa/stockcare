package com.chanebplus.stockcare.modules.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationInput;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationResult;
import com.chanebplus.stockcare.modules.route.service.MockRouteOptimizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockRouteOptimizationServiceTest {

    private final MockRouteOptimizationService service = new MockRouteOptimizationService(new ModelsProperties());

    @Test
    void buildsNonOptimizedRouteWithinCapacity() {
        UUID v = UUID.randomUUID();
        var input = new RouteOptimizationInput(UUID.randomUUID(), 36.8, 10.18,
                List.of(
                    new RouteOptimizationInput.Stop(UUID.randomUUID(), UUID.randomUUID(), 36.86, 10.19, 80, 50, false, null, null),
                    new RouteOptimizationInput.Stop(UUID.randomUUID(), UUID.randomUUID(), 34.74, 10.76, 40, 60, false, null, null)),
                List.of(new RouteOptimizationInput.Vehicle(v, 300, false)), null);

        RouteOptimizationResult result = service.optimize(input);
        assertThat(result.optimized()).isFalse();
        assertThat(result.routes()).hasSize(1);
        assertThat(result.routes().get(0).stops()).hasSize(2);
        assertThat(result.unfulfilledRequestIds()).isEmpty();
        assertThat(result.note()).contains("Non-optimized");
    }

    @Test
    void leavesColdChainUnfulfilledWhenVehicleNotRefrigerated() {
        var input = new RouteOptimizationInput(UUID.randomUUID(), 36.8, 10.18,
                List.of(new RouteOptimizationInput.Stop(UUID.randomUUID(), UUID.randomUUID(), 36.86, 10.19, 90, 10, true, null, null)),
                List.of(new RouteOptimizationInput.Vehicle(UUID.randomUUID(), 300, false)), null);
        RouteOptimizationResult result = service.optimize(input);
        assertThat(result.unfulfilledRequestIds()).hasSize(1);
    }
}
