package com.chanebplus.stockcare.modules.request.service;

import com.chanebplus.stockcare.modules.medication.service.MedicationMapper;
import com.chanebplus.stockcare.modules.priority.service.PriorityService;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.dto.RequestDto;
import com.chanebplus.stockcare.modules.request.dto.RequestItemDto;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the full RequestDto (items + priority) for API responses. */
@Component
public class RequestAssembler {

    private final PriorityService priorityService;

    public RequestAssembler(PriorityService priorityService) {
        this.priorityService = priorityService;
    }

    public RequestDto toDto(PharmacyRequest r) {
        List<RequestItemDto> items = r.getItems().stream()
                .map(i -> new RequestItemDto(i.getId(), MedicationMapper.toDto(i.getMedication()),
                        i.getRequestedQuantity(), i.getNote()))
                .toList();
        return new RequestDto(
                r.getId(),
                r.getPharmacy().getId(),
                r.getPharmacy().getName(),
                r.getDepot().getId(),
                r.getStatus(),
                r.getUrgency(),
                r.getNotes(),
                r.getInternalNotes(),
                r.getAffectedPatients(),
                r.getSourcePredictionId(),
                r.getSubmittedAt(),
                r.getCreatedAt(),
                items,
                priorityService.findByRequestOrNull(r.getId()));
    }
}
