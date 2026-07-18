package com.chanebplus.stockcare.modules.medication.repo;

import com.chanebplus.stockcare.modules.medication.domain.Medication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    Optional<Medication> findBySku(String sku);

    Page<Medication> findByNameContainingIgnoreCaseOrGenericNameContainingIgnoreCase(
            String name, String genericName, Pageable pageable);
}
