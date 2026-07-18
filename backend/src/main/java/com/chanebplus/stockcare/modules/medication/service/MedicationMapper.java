package com.chanebplus.stockcare.modules.medication.service;

import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;

public final class MedicationMapper {

    private MedicationMapper() {}

    public static MedicationDto toDto(Medication m) {
        return new MedicationDto(m.getId(), m.getName(), m.getGenericName(), m.getDosage(),
                m.getPharmaceuticalForm(), m.getPackageSize(), m.getSku(), m.getBarcode(),
                m.getAtcCode(), m.getCategory(), m.isColdChain(), m.getCriticalityScore());
    }
}
