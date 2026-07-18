package com.chanebplus.stockcare.modules.inventory.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.inventory.dto.CreateInventoryItemRequest;
import com.chanebplus.stockcare.modules.inventory.dto.InventoryItemDto;
import com.chanebplus.stockcare.modules.inventory.dto.StockAdjustmentDto;
import com.chanebplus.stockcare.modules.inventory.dto.StockAdjustmentRequest;
import com.chanebplus.stockcare.modules.inventory.dto.UpdateInventoryItemRequest;
import com.chanebplus.stockcare.modules.inventory.service.InventoryService;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pharmacy inventory management. The {pharmacyId} path is validated against the caller's ownership;
 * pharmacy users implicitly operate on their own pharmacy via /api/inventory/me.
 */
@Tag(name = "Pharmacy inventory")
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;
    private final PharmacyAccessService accessService;

    public InventoryController(InventoryService service, PharmacyAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @Operation(summary = "List inventory for the current pharmacy user")
    @GetMapping("/me")
    public List<InventoryItemDto> myInventory() {
        return service.listForPharmacy(accessService.requireOwnPharmacyId());
    }

    @GetMapping("/pharmacies/{pharmacyId}")
    public List<InventoryItemDto> forPharmacy(@PathVariable UUID pharmacyId) {
        return service.listForPharmacy(pharmacyId);
    }

    @GetMapping("/pharmacies/{pharmacyId}/low-stock")
    public List<InventoryItemDto> lowStock(@PathVariable UUID pharmacyId) {
        return service.listLowStock(pharmacyId);
    }

    @Operation(summary = "Add a medication to a pharmacy's inventory")
    @PostMapping("/pharmacies/{pharmacyId}/items")
    public InventoryItemDto create(@PathVariable UUID pharmacyId,
                                   @Valid @RequestBody CreateInventoryItemRequest req) {
        return service.create(pharmacyId, req);
    }

    @PutMapping("/pharmacies/{pharmacyId}/items/{itemId}")
    public InventoryItemDto update(@PathVariable UUID pharmacyId, @PathVariable UUID itemId,
                                   @Valid @RequestBody UpdateInventoryItemRequest req) {
        return service.update(pharmacyId, itemId, req);
    }

    @DeleteMapping("/pharmacies/{pharmacyId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID pharmacyId, @PathVariable UUID itemId) {
        service.delete(pharmacyId, itemId);
    }

    @Operation(summary = "Record a stock adjustment (sale, restock, correction...)")
    @PostMapping("/pharmacies/{pharmacyId}/items/{itemId}/adjustments")
    public InventoryItemDto adjust(@PathVariable UUID pharmacyId, @PathVariable UUID itemId,
                                   @Valid @RequestBody StockAdjustmentRequest req) {
        return service.adjust(pharmacyId, itemId, req);
    }

    @GetMapping("/pharmacies/{pharmacyId}/items/{itemId}/adjustments")
    public PageResponse<StockAdjustmentDto> history(@PathVariable UUID pharmacyId,
                                                    @PathVariable UUID itemId,
                                                    @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.history(pharmacyId, itemId, pageable));
    }
}
