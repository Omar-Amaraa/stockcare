package com.chanebplus.stockcare.modules.depot.web;

import com.chanebplus.stockcare.modules.depot.dto.DepotDto;
import com.chanebplus.stockcare.modules.depot.service.DepotService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Depots")
@RestController
@RequestMapping("/api/depots")
public class DepotController {

    private final DepotService service;

    public DepotController(DepotService service) {
        this.service = service;
    }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @GetMapping("/me")
    public DepotDto me() {
        return service.me();
    }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @GetMapping("/{id}")
    public DepotDto get(@PathVariable UUID id) {
        return service.get(id);
    }
}
