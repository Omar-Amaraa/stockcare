package com.chanebplus.stockcare.modules.medication.dto;

import java.util.UUID;

public record MedicationDto(
        UUID id,
        String name,
        String genericName,
        String dosage,
        String pharmaceuticalForm,
        String packageSize,
        String sku,
        String barcode,
        String atcCode,
        String category,
        boolean coldChain,
        Double criticalityScore) {}
