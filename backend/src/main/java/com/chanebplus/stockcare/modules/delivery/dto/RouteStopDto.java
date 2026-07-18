package com.chanebplus.stockcare.modules.delivery.dto;

import com.chanebplus.stockcare.modules.delivery.domain.RouteStopStatus;
import java.time.Instant;
import java.util.UUID;

public record RouteStopDto(UUID id, int sequence, UUID pharmacyId, String pharmacyName, UUID requestId,
                           double latitude, double longitude, RouteStopStatus status,
                           Double estimatedArrivalMinute, Double distanceFromPrevKm, Instant arrivedAt) {}
