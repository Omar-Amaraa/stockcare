package com.chanebplus.stockcare.modules.delivery.dto;

import java.util.UUID;

public record DeliveryItemDto(UUID id, UUID pharmacyId, UUID requestId, UUID medicationId,
                              String medicationName, int quantity) {}
