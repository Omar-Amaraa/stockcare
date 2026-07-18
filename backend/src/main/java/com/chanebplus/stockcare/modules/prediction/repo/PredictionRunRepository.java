package com.chanebplus.stockcare.modules.prediction.repo;

import com.chanebplus.stockcare.modules.prediction.domain.PredictionRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRunRepository extends JpaRepository<PredictionRun, UUID> {
    Optional<PredictionRun> findByPharmacyId(UUID pharmacyId);
}
