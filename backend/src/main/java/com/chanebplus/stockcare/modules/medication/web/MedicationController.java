package com.chanebplus.stockcare.modules.medication.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.medication.dto.MedicationDto;
import com.chanebplus.stockcare.modules.medication.dto.MedicationRequestDto;
import com.chanebplus.stockcare.modules.medication.service.MedicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Medication catalogue")
@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService service;

    public MedicationController(MedicationService service) {
        this.service = service;
    }

    @Operation(summary = "Search the medication catalogue (paged)")
    @GetMapping
    public PageResponse<MedicationDto> search(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(service.search(query, pageable));
    }

    @GetMapping("/{id}")
    public MedicationDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Create a catalogue entry")
    @PreAuthorize("hasAnyRole('PHARMACY','DEPOT','ADMIN')")
    @PostMapping
    public MedicationDto create(@Valid @RequestBody MedicationRequestDto dto) {
        return service.create(dto);
    }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PutMapping("/{id}")
    public MedicationDto update(@PathVariable UUID id, @Valid @RequestBody MedicationRequestDto dto) {
        return service.update(id, dto);
    }
}
