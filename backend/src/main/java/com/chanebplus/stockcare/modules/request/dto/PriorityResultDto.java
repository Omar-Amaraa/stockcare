package com.chanebplus.stockcare.modules.request.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PriorityResultDto(
        UUID id,
        UUID requestId,
        double coefficient,
        Map<String, Double> factors,
        String explanation,
        String calculationVersion,
        boolean simulated,
        Instant calculatedAt) {}
