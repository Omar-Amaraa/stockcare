package com.chanebplus.stockcare.modules.delivery.web;

import com.chanebplus.stockcare.modules.delivery.dto.DriverDto;
import com.chanebplus.stockcare.modules.delivery.dto.VehicleDto;
import com.chanebplus.stockcare.modules.delivery.service.DeliveryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fleet")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
public class FleetController {

    private final DeliveryService service;

    public FleetController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping("/vehicles")
    public List<VehicleDto> vehicles() {
        return service.vehicles();
    }

    @GetMapping("/drivers")
    public List<DriverDto> drivers() {
        return service.drivers();
    }
}
