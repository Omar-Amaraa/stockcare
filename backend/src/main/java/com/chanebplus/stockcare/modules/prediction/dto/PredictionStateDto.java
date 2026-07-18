package com.chanebplus.stockcare.modules.prediction.dto;

import com.chanebplus.stockcare.modules.prediction.domain.PredictionRunStatus;
import java.time.Instant;
import java.util.List;

/** Combined async state + current results for a pharmacy's shortage predictions. */
public record PredictionStateDto(
        PredictionRunStatus status,
        boolean outdated,
        String correlationId,
        String modelVersion,
        int retryCount,
        int shortageCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        List<PredictionResultDto> predictions) {}
