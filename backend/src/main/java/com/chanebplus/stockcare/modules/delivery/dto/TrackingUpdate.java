package com.chanebplus.stockcare.modules.delivery.dto;

import com.chanebplus.stockcare.modules.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/** Payload pushed over SSE on each simulator tick. */
public record TrackingUpdate(
        UUID deliveryId,
        DeliveryStatus status,
        Double latitude,
        Double longitude,
        Integer currentStopIndex,
        double progress,
        Double etaMinutes,
        Instant timestamp) {}
