package com.chanebplus.stockcare.modules.delivery.dto;

import com.chanebplus.stockcare.modules.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryDto(
        UUID id,
        String reference,
        DeliveryStatus status,
        UUID depotId,
        Double depotLatitude,
        Double depotLongitude,
        VehicleDto vehicle,
        DriverDto driver,
        boolean simulated,
        boolean optimized,
        String optimizerVersion,
        Double totalDistanceKm,
        Double totalDurationMinutes,
        Double currentLatitude,
        Double currentLongitude,
        Integer currentStopIndex,
        double progress,
        Double etaMinutes,
        Instant plannedAt,
        Instant startedAt,
        Instant completedAt,
        List<RouteStopDto> stops,
        List<DeliveryItemDto> items) {}
