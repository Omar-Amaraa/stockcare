package com.chanebplus.stockcare.modules.depot.dto;

import java.util.UUID;

public record DepotDto(
        UUID id,
        String code,
        String name,
        String addressLine,
        String region,
        String city,
        Double latitude,
        Double longitude,
        String phone,
        long pharmacyCount) {}
