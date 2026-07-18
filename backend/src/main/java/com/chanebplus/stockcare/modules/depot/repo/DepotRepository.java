package com.chanebplus.stockcare.modules.depot.repo;

import com.chanebplus.stockcare.modules.depot.domain.Depot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRepository extends JpaRepository<Depot, UUID> {
    Optional<Depot> findByCode(String code);
}
