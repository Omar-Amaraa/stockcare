package com.chanebplus.stockcare.modules.delivery.dto;

import java.util.UUID;

public record VehicleDto(UUID id, String code, String plateNumber, int capacityUnits,
                         boolean refrigerated, boolean active) {}
