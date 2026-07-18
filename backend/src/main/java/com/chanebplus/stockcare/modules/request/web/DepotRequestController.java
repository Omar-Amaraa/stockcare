package com.chanebplus.stockcare.modules.request.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import com.chanebplus.stockcare.modules.request.dto.ChangeStatusDto;
import com.chanebplus.stockcare.modules.request.dto.InternalNoteDto;
import com.chanebplus.stockcare.modules.request.dto.PriorityResultDto;
import com.chanebplus.stockcare.modules.request.dto.RequestDto;
import com.chanebplus.stockcare.modules.request.service.RequestService;
import com.chanebplus.stockcare.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Depot-facing request management: filtering, status changes, notes, prioritization, planning. */
@Tag(name = "Depot request management")
@RestController
@RequestMapping("/api/depot/requests")
@PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
public class DepotRequestController {

    private final RequestService service;

    public DepotRequestController(RequestService service) {
        this.service = service;
    }

    @Operation(summary = "List and filter requests addressed to the current depot")
    @GetMapping
    public PageResponse<RequestDto> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Urgency urgency,
            @RequestParam(required = false) UUID pharmacyId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        UUID depotId = SecurityUtils.currentDepotId();
        return PageResponse.from(service.listForDepot(depotId, status, urgency, pharmacyId, pageable));
    }

    @GetMapping("/{id}")
    public RequestDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Change a request's status")
    @PostMapping("/{id}/status")
    public RequestDto changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusDto dto) {
        return service.changeStatus(id, dto);
    }

    @Operation(summary = "Set the depot internal note on a request")
    @PostMapping("/{id}/internal-note")
    public RequestDto internalNote(@PathVariable UUID id, @Valid @RequestBody InternalNoteDto dto) {
        return service.addInternalNote(id, dto.note());
    }

    @Operation(summary = "Calculate (mock) the priority coefficient for a request")
    @PostMapping("/{id}/prioritize")
    public PriorityResultDto prioritize(@PathVariable UUID id) {
        return service.prioritize(id);
    }

    @Operation(summary = "Approve a prioritized request for route planning")
    @PostMapping("/{id}/approve")
    public RequestDto approve(@PathVariable UUID id) {
        return service.approveForPlanning(id);
    }
}
