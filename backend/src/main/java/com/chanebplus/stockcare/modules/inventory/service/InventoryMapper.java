package com.chanebplus.stockcare.modules.inventory.service;

import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.inventory.domain.StockAdjustment;
import com.chanebplus.stockcare.modules.inventory.dto.InventoryItemDto;
import com.chanebplus.stockcare.modules.inventory.dto.StockAdjustmentDto;
import com.chanebplus.stockcare.modules.medication.service.MedicationMapper;

public final class InventoryMapper {

    private InventoryMapper() {}

    public static boolean isLowStock(InventoryItem item) {
        int threshold = item.getReorderThreshold() != null
                ? item.getReorderThreshold() : item.getMinimumQuantity();
        return item.getCurrentQuantity() <= threshold;
    }

    public static InventoryItemDto toDto(InventoryItem item) {
        return new InventoryItemDto(
                item.getId(),
                item.getPharmacy().getId(),
                MedicationMapper.toDto(item.getMedication()),
                item.getCurrentQuantity(),
                item.getMinimumQuantity(),
                item.getAverageDailyConsumption(),
                item.getReorderThreshold(),
                item.getExpirationDate(),
                isLowStock(item));
    }

    public static StockAdjustmentDto toDto(StockAdjustment a) {
        return new StockAdjustmentDto(a.getId(), a.getDelta(), a.getQuantityBefore(),
                a.getQuantityAfter(), a.getReason(), a.getNote(), a.getOccurredAt());
    }
}
