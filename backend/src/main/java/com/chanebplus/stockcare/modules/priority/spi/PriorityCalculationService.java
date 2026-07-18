package com.chanebplus.stockcare.modules.priority.spi;

import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;

/**
 * Replaceable priority-scoring boundary. The MVP ships a deterministic, transparent mock formula.
 * A future learned model (Python service, REST API, queue consumer or Java model) can replace it
 * behind this interface via {@code stockcare.models.priority.mode}, without touching the workflow.
 */
public interface PriorityCalculationService {

    PriorityOutcome calculatePriority(PharmacyRequest request);

    String mode();
}
