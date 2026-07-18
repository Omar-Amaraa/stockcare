package com.chanebplus.stockcare.modules.priority.spi;

import java.util.Map;

/** Result of a priority calculation. Factors are surfaced for full transparency/explainability. */
public record PriorityOutcome(
        double coefficient,
        Map<String, Double> factors,
        String explanation,
        String version,
        boolean simulated) {}
