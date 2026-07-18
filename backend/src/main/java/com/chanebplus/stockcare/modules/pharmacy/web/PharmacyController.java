package com.chanebplus.stockcare.modules.pharmacy.web;

import com.chanebplus.stockcare.modules.pharmacy.dto.PharmacyDto;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pharmacies")
@RestController
@RequestMapping("/api/pharmacies")
public class PharmacyController {

    private final PharmacyService service;

    public PharmacyController(PharmacyService service) {
        this.service = service;
    }

    @Operation(summary = "List pharmacies visible to the caller")
    @GetMapping
    public List<PharmacyDto> list() {
        return service.listVisible();
    }

    @GetMapping("/me")
    public PharmacyDto me() {
        return service.me();
    }

    @GetMapping("/{id}")
    public PharmacyDto get(@PathVariable UUID id) {
        return service.get(id);
    }
}
