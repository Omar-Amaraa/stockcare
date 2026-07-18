package com.chanebplus.stockcare.modules.prediction.dto;

import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PredictionResultDto(
        UUID id,
        UUID pharmacyId,
        MedicationDto medication,
        int currentStock,
        LocalDate predictedShortageDate,
        Integer estimatedRemainingDays,
        int predictedMissingQuantity,
        Double confidence,
        String reason,
        String modelVersion,
        boolean simulated,
        Instant predictionTime) {}
