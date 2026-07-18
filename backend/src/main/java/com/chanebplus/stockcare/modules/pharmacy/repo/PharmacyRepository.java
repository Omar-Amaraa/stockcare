package com.chanebplus.stockcare.modules.pharmacy.repo;

import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID> {
    Optional<Pharmacy> findByCode(String code);
}
