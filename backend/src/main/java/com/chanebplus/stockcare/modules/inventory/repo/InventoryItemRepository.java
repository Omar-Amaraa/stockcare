package com.chanebplus.stockcare.modules.inventory.repo;

import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    @EntityGraph(attributePaths = "medication")
    List<InventoryItem> findByPharmacyId(UUID pharmacyId);

    boolean existsByPharmacyIdAndMedicationId(UUID pharmacyId, UUID medicationId);

    @EntityGraph(attributePaths = {"medication", "pharmacy"})
    Optional<InventoryItem> findByIdAndPharmacyId(UUID id, UUID pharmacyId);
}
