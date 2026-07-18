package com.chanebplus.stockcare.modules.medication.service;

import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;
import com.chanebplus.stockcare.modules.medication.dto.MedicationRequestDto;
import com.chanebplus.stockcare.modules.medication.repo.MedicationRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationService {

    private final MedicationRepository repository;

    public MedicationService(MedicationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<MedicationDto> search(String query, Pageable pageable) {
        Page<Medication> page = (query == null || query.isBlank())
                ? repository.findAll(pageable)
                : repository.findByNameContainingIgnoreCaseOrGenericNameContainingIgnoreCase(query, query, pageable);
        return page.map(MedicationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public MedicationDto get(UUID id) {
        return MedicationMapper.toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Medication getEntity(UUID id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Medication", id));
    }

    @Transactional
    public MedicationDto create(MedicationRequestDto dto) {
        Medication m = new Medication();
        apply(m, dto);
        return MedicationMapper.toDto(repository.save(m));
    }

    @Transactional
    public MedicationDto update(UUID id, MedicationRequestDto dto) {
        Medication m = getEntity(id);
        apply(m, dto);
        return MedicationMapper.toDto(repository.save(m));
    }

    private void apply(Medication m, MedicationRequestDto dto) {
        m.setName(dto.name());
        m.setGenericName(dto.genericName());
        m.setDosage(dto.dosage());
        m.setPharmaceuticalForm(dto.pharmaceuticalForm());
        m.setPackageSize(dto.packageSize());
        m.setSku(dto.sku());
        m.setBarcode(dto.barcode());
        m.setAtcCode(dto.atcCode());
        m.setCategory(dto.category());
        m.setColdChain(dto.coldChain());
        m.setCriticalityScore(dto.criticalityScore());
    }
}
