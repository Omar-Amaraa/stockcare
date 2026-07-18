package com.chanebplus.stockcare.modules.pharmacy.service;

import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.pharmacy.dto.PharmacyDto;
import com.chanebplus.stockcare.modules.pharmacy.repo.PharmacyRepository;
import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.security.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final DepotPharmacyRepository depotPharmacyRepository;
    private final PharmacyAccessService accessService;

    public PharmacyService(PharmacyRepository pharmacyRepository,
                           DepotPharmacyRepository depotPharmacyRepository,
                           PharmacyAccessService accessService) {
        this.pharmacyRepository = pharmacyRepository;
        this.depotPharmacyRepository = depotPharmacyRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public PharmacyDto get(UUID id) {
        accessService.assertCanAccessPharmacy(id);
        return PharmacyMapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public PharmacyDto me() {
        return PharmacyMapper.toDto(load(accessService.requireOwnPharmacyId()));
    }

    /** Pharmacies visible to the caller: own (pharmacy), depot's associated set (depot), or all (admin). */
    @Transactional(readOnly = true)
    public List<PharmacyDto> listVisible() {
        Role role = SecurityUtils.currentRole();
        return switch (role) {
            case ADMIN -> pharmacyRepository.findAll().stream().map(PharmacyMapper::toDto).toList();
            case DEPOT -> depotPharmacyRepository.findPharmaciesByDepotId(SecurityUtils.currentDepotId())
                    .stream().map(PharmacyMapper::toDto).toList();
            case PHARMACY -> List.of(PharmacyMapper.toDto(load(SecurityUtils.currentPharmacyId())));
        };
    }

    private Pharmacy load(UUID id) {
        return pharmacyRepository.findById(id).orElseThrow(() -> NotFoundException.of("Pharmacy", id));
    }
}
