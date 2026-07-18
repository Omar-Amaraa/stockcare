package com.chanebplus.stockcare.modules.delivery.dto;

import com.chanebplus.stockcare.modules.delivery.domain.DeliveryEventType;
import java.time.Instant;
import java.util.UUID;

public record DeliveryEventDto(UUID id, DeliveryEventType type, String message, Instant occurredAt,
                               Double latitude, Double longitude) {}
