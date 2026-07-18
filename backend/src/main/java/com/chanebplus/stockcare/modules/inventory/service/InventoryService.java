package com.chanebplus.stockcare.modules.inventory.service;

import com.chanebplus.stockcare.common.error.BusinessRuleException;
import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.inventory.domain.AdjustmentReason;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.inventory.domain.StockAdjustment;
import com.chanebplus.stockcare.modules.inventory.dto.CreateInventoryItemRequest;
import com.chanebplus.stockcare.modules.inventory.dto.InventoryItemDto;
import com.chanebplus.stockcare.modules.inventory.dto.StockAdjustmentDto;
import com.chanebplus.stockcare.modules.inventory.dto.StockAdjustmentRequest;
import com.chanebplus.stockcare.modules.inventory.dto.UpdateInventoryItemRequest;
import com.chanebplus.stockcare.modules.inventory.repo.InventoryItemRepository;
import com.chanebplus.stockcare.modules.inventory.repo.StockAdjustmentRepository;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.medication.service.MedicationService;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import com.chanebplus.stockcare.common.events.WorkflowEvents;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final MedicationService medicationService;
    private final PharmacyAccessService accessService;
    private final ApplicationClock clock;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public InventoryService(InventoryItemRepository itemRepository,
                            StockAdjustmentRepository adjustmentRepository,
                            MedicationService medicationService,
                            PharmacyAccessService accessService,
                            ApplicationClock clock,
                            EntityManager entityManager,
                            ApplicationEventPublisher events) {
        this.itemRepository = itemRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.medicationService = medicationService;
        this.accessService = accessService;
        this.clock = clock;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemDto> listForPharmacy(UUID pharmacyId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        return itemRepository.findByPharmacyId(pharmacyId).stream()
                .map(InventoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryItemDto> listLowStock(UUID pharmacyId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        return itemRepository.findByPharmacyId(pharmacyId).stream()
                .filter(InventoryMapper::isLowStock)
                .map(InventoryMapper::toDto).toList();
    }

    @Transactional
    public InventoryItemDto create(UUID pharmacyId, CreateInventoryItemRequest req) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        if (itemRepository.existsByPharmacyIdAndMedicationId(pharmacyId, req.medicationId())) {
            throw new BusinessRuleException("This medication is already in the pharmacy inventory");
        }
        Medication medication = medicationService.getEntity(req.medicationId());
        InventoryItem item = new InventoryItem();
        item.setPharmacy(entityManager.getReference(Pharmacy.class, pharmacyId));
        item.setMedication(medication);
        item.setCurrentQuantity(req.currentQuantity());
        item.setMinimumQuantity(req.minimumQuantity());
        item.setAverageDailyConsumption(req.averageDailyConsumption());
        item.setReorderThreshold(req.reorderThreshold());
        item.setExpirationDate(req.expirationDate());
        item = itemRepository.save(item);
        if (req.currentQuantity() != 0) {
            recordAdjustment(item, req.currentQuantity(), 0, req.currentQuantity(),
                    AdjustmentReason.INITIAL, "Initial stock");
        }
        events.publishEvent(new WorkflowEvents.InventoryChanged(pharmacyId));
        return InventoryMapper.toDto(item);
    }

    @Transactional
    public InventoryItemDto update(UUID pharmacyId, UUID itemId, UpdateInventoryItemRequest req) {
        InventoryItem item = load(pharmacyId, itemId);
        item.setMinimumQuantity(req.minimumQuantity());
        item.setAverageDailyConsumption(req.averageDailyConsumption());
        item.setReorderThreshold(req.reorderThreshold());
        item.setExpirationDate(req.expirationDate());
        InventoryItemDto dto = InventoryMapper.toDto(itemRepository.save(item));
        events.publishEvent(new WorkflowEvents.InventoryChanged(pharmacyId));
        return dto;
    }

    @Transactional
    public void delete(UUID pharmacyId, UUID itemId) {
        InventoryItem item = load(pharmacyId, itemId);
        itemRepository.delete(item);
        events.publishEvent(new WorkflowEvents.InventoryChanged(pharmacyId));
    }

    @Transactional
    public InventoryItemDto adjust(UUID pharmacyId, UUID itemId, StockAdjustmentRequest req) {
        InventoryItem item = load(pharmacyId, itemId);
        int before = item.getCurrentQuantity();
        int after = before + req.delta();
        if (after < 0) {
            throw new BusinessRuleException("Adjustment would drive stock below zero");
        }
        item.setCurrentQuantity(after);
        itemRepository.save(item);
        recordAdjustment(item, req.delta(), before, after, req.reason(), req.note());
        events.publishEvent(new WorkflowEvents.InventoryChanged(pharmacyId));
        return InventoryMapper.toDto(item);
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentDto> history(UUID pharmacyId, UUID itemId, Pageable pageable) {
        load(pharmacyId, itemId);
        return adjustmentRepository.findByInventoryItemIdOrderByOccurredAtDesc(itemId, pageable)
                .map(InventoryMapper::toDto);
    }

    private InventoryItem load(UUID pharmacyId, UUID itemId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        return itemRepository.findByIdAndPharmacyId(itemId, pharmacyId)
                .orElseThrow(() -> NotFoundException.of("InventoryItem", itemId));
    }

    private void recordAdjustment(InventoryItem item, int delta, int before, int after,
                                  AdjustmentReason reason, String note) {
        StockAdjustment adj = new StockAdjustment();
        adj.setInventoryItem(item);
        adj.setDelta(delta);
        adj.setQuantityBefore(before);
        adj.setQuantityAfter(after);
        adj.setReason(reason);
        adj.setNote(note);
        adj.setOccurredAt(clock.now());
        adjustmentRepository.save(adj);
    }
}
