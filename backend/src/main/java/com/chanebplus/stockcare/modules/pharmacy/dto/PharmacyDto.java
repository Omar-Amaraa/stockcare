package com.chanebplus.stockcare.modules.pharmacy.dto;

import java.util.UUID;

public record PharmacyDto(
        UUID id,
        String code,
        String name,
        String licenseNumber,
        String addressLine,
        String region,
        String city,
        Double latitude,
        Double longitude,
        String phone) {}
