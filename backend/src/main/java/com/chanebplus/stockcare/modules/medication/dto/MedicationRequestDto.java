package com.chanebplus.stockcare.modules.medication.dto;

import jakarta.validation.constraints.NotBlank;

public record MedicationRequestDto(
        @NotBlank String name,
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
