package com.chanebplus.stockcare.modules.prediction.spi;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Transport object returned by any {@link StockPredictionService} implementation. Kept free of JPA
 * so it can be produced by a mock, a Python REST service or a message-queue consumer alike.
 */
public record ShortagePrediction(
        UUID medicationId,
        int currentStock,
        LocalDate predictedShortageDate,
        Integer estimatedRemainingDays,
        int predictedMissingQuantity,
        Double confidence,
        String reason,
        String modelVersion,
        boolean simulated) {}
