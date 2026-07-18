package com.chanebplus.stockcare.modules.pharmacy.service;

import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.pharmacy.dto.PharmacyDto;

public final class PharmacyMapper {
    private PharmacyMapper() {}

    public static PharmacyDto toDto(Pharmacy p) {
        return new PharmacyDto(p.getId(), p.getCode(), p.getName(), p.getLicenseNumber(),
                p.getAddressLine(), p.getRegion(), p.getCity(), p.getLatitude(), p.getLongitude(), p.getPhone());
    }
}
