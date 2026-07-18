package com.chanebplus.stockcare.modules.priority.service;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.priority.spi.PriorityCalculationService;
import com.chanebplus.stockcare.modules.priority.spi.PriorityOutcome;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic, transparent priority formula (NOT a trained model). Combines urgency, medication
 * criticality, cold-chain sensitivity, requested volume, patient impact and request age into a
 * 0..100 coefficient, exposing each weighted factor for auditability.
 */
@Service
@ConditionalOnProperty(name = "stockcare.models.priority.mode", havingValue = "mock", matchIfMissing = true)
public class MockPriorityCalculationService implements PriorityCalculationService {

    private final ModelsProperties models;
    private final ApplicationClock clock;

    public MockPriorityCalculationService(ModelsProperties models, ApplicationClock clock) {
        this.models = models;
        this.clock = clock;
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public PriorityOutcome calculatePriority(PharmacyRequest request) {
        double urgencyScore = urgencyScore(request.getUrgency());

        double criticality = 0.5;
        boolean coldChain = false;
        int totalQty = 0;
        for (PharmacyRequestItem item : request.getItems()) {
            Medication med = item.getMedication();
            if (med.getCriticalityScore() != null) {
                criticality = Math.max(criticality, med.getCriticalityScore());
            }
            coldChain = coldChain || med.isColdChain();
            totalQty += item.getRequestedQuantity();
        }
        double coldChainScore = coldChain ? 1.0 : 0.0;
        double quantityScore = Math.min(1.0, totalQty / 100.0);
        double patientScore = request.getAffectedPatients() != null
                ? Math.min(1.0, request.getAffectedPatients() / 50.0) : 0.0;

        Instant reference = request.getSubmittedAt() != null ? request.getSubmittedAt() : clock.now();
        double ageHours = Math.max(0, Duration.between(reference, clock.now()).toMinutes() / 60.0);
        double ageScore = Math.min(1.0, ageHours / 72.0);

        // Weights sum to 1.0; result scaled to 0..100.
        double w = 0;
        Map<String, Double> factors = new LinkedHashMap<>();
        w += weighted(factors, "urgency", urgencyScore, 0.30);
        w += weighted(factors, "medicationCriticality", criticality, 0.25);
        w += weighted(factors, "coldChainSensitivity", coldChainScore, 0.15);
        w += weighted(factors, "requestedVolume", quantityScore, 0.10);
        w += weighted(factors, "patientImpact", patientScore, 0.10);
        w += weighted(factors, "requestAge", ageScore, 0.10);

        double coefficient = Math.round(w * 100.0 * 100.0) / 100.0;

        String explanation = String.format(
                "[SIMULATED deterministic] Priority %.2f/100 from urgency=%.2f, criticality=%.2f, "
                        + "coldChain=%.0f, volume=%.2f, patients=%.2f, age=%.2f (weighted).",
                coefficient, urgencyScore, criticality, coldChainScore, quantityScore, patientScore, ageScore);

        return new PriorityOutcome(coefficient, factors, explanation,
                models.getPriority().getVersion(), true);
    }

    private double weighted(Map<String, Double> factors, String name, double score, double weight) {
        double contribution = score * weight;
        factors.put(name, Math.round(contribution * 100.0 * 100.0) / 100.0);
        return contribution;
    }

    private double urgencyScore(Urgency urgency) {
        return switch (urgency) {
            case LOW -> 0.25;
            case NORMAL -> 0.5;
            case HIGH -> 0.75;
            case CRITICAL -> 1.0;
        };
    }
}
