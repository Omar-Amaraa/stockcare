package com.chanebplus.stockcare.modules.pharmacy.service;

import com.chanebplus.stockcare.common.error.ForbiddenAccessException;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.security.SecurityUtils;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Central enforcement of pharmacy-level data ownership. A PHARMACY user may only touch its own
 * pharmacy; a DEPOT user may touch pharmacies associated with its depot; ADMIN is unrestricted.
 */
@Service
public class PharmacyAccessService {

    private final DepotPharmacyRepository depotPharmacyRepository;

    public PharmacyAccessService(DepotPharmacyRepository depotPharmacyRepository) {
        this.depotPharmacyRepository = depotPharmacyRepository;
    }

    public void assertCanAccessPharmacy(UUID pharmacyId) {
        Role role = SecurityUtils.currentRole();
        switch (role) {
            case ADMIN -> { /* unrestricted */ }
            case PHARMACY -> {
                if (!SecurityUtils.currentPharmacyId().equals(pharmacyId)) {
                    throw new ForbiddenAccessException("Pharmacy users may only access their own pharmacy");
                }
            }
            case DEPOT -> {
                if (!depotPharmacyRepository.existsByDepotIdAndPharmacyId(
                        SecurityUtils.currentDepotId(), pharmacyId)) {
                    throw new ForbiddenAccessException("Pharmacy is not associated with this depot");
                }
            }
            default -> throw new ForbiddenAccessException("Unknown role");
        }
    }

    /** Returns the pharmacy the current PHARMACY user is bound to. */
    public UUID requireOwnPharmacyId() {
        if (SecurityUtils.currentRole() != Role.PHARMACY) {
            throw new ForbiddenAccessException("Only pharmacy users have an own pharmacy");
        }
        return SecurityUtils.currentPharmacyId();
    }
}
