package com.chanebplus.stockcare.modules.prediction.repo;

import com.chanebplus.stockcare.modules.prediction.domain.PredictionResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PredictionResultRepository extends JpaRepository<PredictionResult, UUID> {

    @EntityGraph(attributePaths = "medication")
    List<PredictionResult> findByPharmacyIdOrderByPredictedShortageDateAsc(UUID pharmacyId);

    @EntityGraph(attributePaths = "medication")
    List<PredictionResult> findByPharmacyIdAndMedicationId(UUID pharmacyId, UUID medicationId);

    @Modifying
    @Query("delete from PredictionResult p where p.pharmacy.id = :pharmacyId")
    void deleteByPharmacyId(@Param("pharmacyId") UUID pharmacyId);
}
