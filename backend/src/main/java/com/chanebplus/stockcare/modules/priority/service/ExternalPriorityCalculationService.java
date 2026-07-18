package com.chanebplus.stockcare.modules.priority.service;

import com.chanebplus.stockcare.modules.priority.spi.PriorityCalculationService;
import com.chanebplus.stockcare.modules.priority.spi.PriorityOutcome;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Prepared integration point for the real depot-side priority model. Not implemented in the MVP. */
@Service
@ConditionalOnProperty(name = "stockcare.models.priority.mode", havingValue = "external")
public class ExternalPriorityCalculationService implements PriorityCalculationService {

    @Override
    public String mode() {
        return "external";
    }

    @Override
    public PriorityOutcome calculatePriority(PharmacyRequest request) {
        throw new UnsupportedOperationException(
                "External priority model not yet wired. Set stockcare.models.priority.mode=mock for the MVP.");
    }
}
