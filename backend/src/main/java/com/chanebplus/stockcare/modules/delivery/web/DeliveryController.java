package com.chanebplus.stockcare.modules.delivery.web;

import com.chanebplus.stockcare.modules.delivery.dto.DeliveryDto;
import com.chanebplus.stockcare.modules.delivery.dto.DeliveryEventDto;
import com.chanebplus.stockcare.modules.delivery.dto.DeliveryPlanRequest;
import com.chanebplus.stockcare.modules.delivery.dto.FleetPlanRequest;
import com.chanebplus.stockcare.modules.delivery.dto.FleetPlanResult;
import com.chanebplus.stockcare.modules.delivery.dto.TrackingPositionDto;
import com.chanebplus.stockcare.modules.delivery.service.DeliveryService;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Deliveries")
@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;
    private final PharmacyAccessService accessService;

    public DeliveryController(DeliveryService service, PharmacyAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @Operation(summary = "Depot: plan one vehicle's delivery from approved requests")
    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/plan")
    public DeliveryDto plan(@Valid @RequestBody DeliveryPlanRequest req) {
        return service.createPlan(req);
    }

    @Operation(summary = "Depot: plan a wave of approved requests across the whole fleet (MILP)",
            description = "Runs a single optimization over all selected requests and vehicles. The "
                    + "optimizer assigns pharmacies to vehicles and orders each route; one delivery "
                    + "is created per returned route. Omit vehicleIds to use every active vehicle.")
    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/plan/fleet")
    public FleetPlanResult planFleet(@Valid @RequestBody FleetPlanRequest req) {
        return service.plan(req);
    }

    @Operation(summary = "Depot: list this depot's deliveries")
    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @GetMapping
    public List<DeliveryDto> depotList() {
        return service.listForDepot();
    }

    @Operation(summary = "Pharmacy: list deliveries concerning my pharmacy")
    @PreAuthorize("hasRole('PHARMACY')")
    @GetMapping("/me")
    public List<DeliveryDto> mine() {
        return service.listForPharmacy(accessService.requireOwnPharmacyId());
    }

    @GetMapping("/{id}")
    public DeliveryDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/events")
    public List<DeliveryEventDto> events(@PathVariable UUID id) {
        return service.events(id);
    }

    @GetMapping("/{id}/positions")
    public List<TrackingPositionDto> positions(@PathVariable UUID id) {
        return service.positions(id);
    }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/{id}/start")
    public DeliveryDto start(@PathVariable UUID id) { return service.start(id); }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/{id}/pause")
    public DeliveryDto pause(@PathVariable UUID id) { return service.pause(id); }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/{id}/resume")
    public DeliveryDto resume(@PathVariable UUID id) { return service.resume(id); }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/{id}/complete")
    public DeliveryDto complete(@PathVariable UUID id) { return service.complete(id); }

    @PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
    @PostMapping("/{id}/cancel")
    public DeliveryDto cancel(@PathVariable UUID id) { return service.cancel(id); }
}
