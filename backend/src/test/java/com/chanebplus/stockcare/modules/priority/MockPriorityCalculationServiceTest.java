package com.chanebplus.stockcare.modules.priority;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.priority.service.MockPriorityCalculationService;
import com.chanebplus.stockcare.modules.priority.spi.PriorityOutcome;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MockPriorityCalculationServiceTest {

    private final MockPriorityCalculationService service =
            new MockPriorityCalculationService(new ModelsProperties(), () -> Instant.parse("2026-01-15T09:00:00Z"));

    @Test
    void criticalColdChainScoresHigherThanLowRoutine() {
        assertThat(score(Urgency.CRITICAL, 0.95, true, 80))
                .isGreaterThan(score(Urgency.LOW, 0.3, false, 5));
    }

    @Test
    void outcomeIsBoundedAndExplained() {
        PharmacyRequest r = request(Urgency.HIGH, 0.8, false, 40);
        PriorityOutcome o = service.calculatePriority(r);
        assertThat(o.coefficient()).isBetween(0.0, 100.0);
        assertThat(o.simulated()).isTrue();
        assertThat(o.factors()).containsKeys("urgency", "medicationCriticality", "coldChainSensitivity");
        assertThat(o.explanation()).contains("SIMULATED");
    }

    private double score(Urgency u, double crit, boolean cold, int qty) {
        return service.calculatePriority(request(u, crit, cold, qty)).coefficient();
    }

    private PharmacyRequest request(Urgency u, double crit, boolean cold, int qty) {
        Medication m = new Medication();
        m.setCriticalityScore(crit);
        m.setColdChain(cold);
        PharmacyRequestItem item = new PharmacyRequestItem();
        item.setMedication(m);
        item.setRequestedQuantity(qty);
        PharmacyRequest r = new PharmacyRequest();
        r.setUrgency(u);
        r.setSubmittedAt(Instant.parse("2026-01-15T09:00:00Z"));
        r.addItem(item);
        return r;
    }
}
