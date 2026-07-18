package com.chanebplus.stockcare.modules.request.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import com.chanebplus.stockcare.modules.request.dto.CreateRequestDto;
import com.chanebplus.stockcare.modules.request.dto.DraftFromPredictionDto;
import com.chanebplus.stockcare.modules.request.dto.RequestDto;
import com.chanebplus.stockcare.modules.request.service.RequestService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pharmacy-facing request endpoints plus shared request detail. */
@Tag(name = "Pharmacy requests")
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService service;
    private final PharmacyAccessService accessService;

    public RequestController(RequestService service, PharmacyAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @Operation(summary = "List the current pharmacy's requests")
    @PreAuthorize("hasRole('PHARMACY')")
    @GetMapping("/me")
    public PageResponse<RequestDto> mine(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.listForPharmacy(accessService.requireOwnPharmacyId(), pageable));
    }

    @Operation(summary = "Get a single request (owner pharmacy, its depot, or admin)")
    @GetMapping("/{id}")
    public RequestDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Create a draft request for the current pharmacy")
    @PreAuthorize("hasRole('PHARMACY')")
    @PostMapping
    public RequestDto create(@Valid @RequestBody CreateRequestDto dto) {
        return service.createDraft(accessService.requireOwnPharmacyId(), dto);
    }

    @Operation(summary = "Create a draft request from a shortage prediction")
    @PreAuthorize("hasRole('PHARMACY')")
    @PostMapping("/from-prediction")
    public RequestDto fromPrediction(@Valid @RequestBody DraftFromPredictionDto dto) {
        return service.draftFromPrediction(accessService.requireOwnPharmacyId(), dto);
    }

    @PreAuthorize("hasRole('PHARMACY')")
    @PutMapping("/{id}")
    public RequestDto update(@PathVariable UUID id, @Valid @RequestBody CreateRequestDto dto) {
        return service.updateDraft(accessService.requireOwnPharmacyId(), id, dto);
    }

    @Operation(summary = "Submit a draft request to the depot")
    @PreAuthorize("hasRole('PHARMACY')")
    @PostMapping("/{id}/submit")
    public RequestDto submit(@PathVariable UUID id) {
        return service.submit(accessService.requireOwnPharmacyId(), id);
    }

    @Operation(summary = "Cancel a request that has not yet been processed")
    @PreAuthorize("hasRole('PHARMACY')")
    @PostMapping("/{id}/cancel")
    public RequestDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
