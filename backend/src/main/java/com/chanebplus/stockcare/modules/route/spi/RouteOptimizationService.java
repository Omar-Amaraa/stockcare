package com.chanebplus.stockcare.modules.route.spi;

import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationInput;
import com.chanebplus.stockcare.modules.route.dto.RouteOptimizationResult;

/**
 * Replaceable route-optimization boundary for the future MILP optimizer. The MVP will provide a mock
 * that sorts approved requests by priority and packs them by vehicle capacity (batch 2), clearly
 * marked as non-optimized test output. Switched via {@code stockcare.models.route-optimization.mode}.
 */
public interface RouteOptimizationService {

    RouteOptimizationResult optimize(RouteOptimizationInput input);

    String mode();
}
