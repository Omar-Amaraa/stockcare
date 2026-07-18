package com.chanebplus.stockcare.modules.prediction.web;

import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import com.chanebplus.stockcare.modules.prediction.dto.PredictionResultDto;
import com.chanebplus.stockcare.modules.prediction.dto.PredictionStateDto;
import com.chanebplus.stockcare.modules.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Predictions run automatically (event-driven). These endpoints expose the current async state and
 * results; {@code /retry} is a fallback for error recovery only, not the normal workflow.
 */
@Tag(name = "Shortage predictions")
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService service;
    private final PharmacyAccessService accessService;

    public PredictionController(PredictionService service, PharmacyAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @Operation(summary = "Current prediction state + results for the signed-in pharmacy")
    @GetMapping("/me/state")
    public PredictionStateDto myState() {
        return service.state(accessService.requireOwnPharmacyId());
    }

    @Operation(summary = "Latest stored predictions for the signed-in pharmacy")
    @GetMapping("/me")
    public List<PredictionResultDto> mine() {
        return service.latest(accessService.requireOwnPharmacyId());
    }

    @Operation(summary = "Fallback: retry a failed prediction (error recovery only)")
    @PostMapping("/me/retry")
    public PredictionStateDto retryMine() {
        return service.retry(accessService.requireOwnPharmacyId());
    }

    @Operation(summary = "Prediction state for a pharmacy (depot/admin view)")
    @GetMapping("/pharmacies/{pharmacyId}/state")
    public PredictionStateDto stateFor(@PathVariable UUID pharmacyId) {
        return service.state(pharmacyId);
    }

    @GetMapping("/pharmacies/{pharmacyId}")
    public List<PredictionResultDto> forPharmacy(@PathVariable UUID pharmacyId) {
        return service.latest(pharmacyId);
    }
}
