package com.chanebplus.stockcare.modules.depot.service;

import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.depot.dto.DepotDto;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.depot.repo.DepotRepository;
import com.chanebplus.stockcare.security.SecurityUtils;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepotService {

    private final DepotRepository depotRepository;
    private final DepotPharmacyRepository depotPharmacyRepository;

    public DepotService(DepotRepository depotRepository, DepotPharmacyRepository depotPharmacyRepository) {
        this.depotRepository = depotRepository;
        this.depotPharmacyRepository = depotPharmacyRepository;
    }

    @Transactional(readOnly = true)
    public DepotDto me() {
        return toDto(load(SecurityUtils.currentDepotId()));
    }

    @Transactional(readOnly = true)
    public DepotDto get(UUID id) {
        return toDto(load(id));
    }

    private Depot load(UUID id) {
        return depotRepository.findById(id).orElseThrow(() -> NotFoundException.of("Depot", id));
    }

    private DepotDto toDto(Depot d) {
        long count = depotPharmacyRepository.countPharmaciesByDepotId(d.getId());
        return new DepotDto(d.getId(), d.getCode(), d.getName(), d.getAddressLine(),
                d.getRegion(), d.getCity(), d.getLatitude(), d.getLongitude(), d.getPhone(), count);
    }
}
