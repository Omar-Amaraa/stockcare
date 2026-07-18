package com.chanebplus.stockcare.modules.inventory.repo;

import com.chanebplus.stockcare.modules.inventory.domain.StockAdjustment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
    Page<StockAdjustment> findByInventoryItemIdOrderByOccurredAtDesc(UUID inventoryItemId, Pageable pageable);
}
