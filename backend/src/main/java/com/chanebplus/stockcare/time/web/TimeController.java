package com.chanebplus.stockcare.time.web;

import com.chanebplus.stockcare.time.dto.AdvanceTimeRequest;
import com.chanebplus.stockcare.time.dto.ClockStatus;
import com.chanebplus.stockcare.time.dto.SetSimulatedTimeRequest;
import com.chanebplus.stockcare.time.service.ApplicationClockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated application-time controls. Available to authenticated test users so predictions and
 * deliveries can be exercised across seasons, months, weekdays and holidays.
 */
@Tag(name = "Simulated time")
@RestController
@RequestMapping("/api/time")
public class TimeController {

    private final ApplicationClockService clock;

    public TimeController(ApplicationClockService clock) {
        this.clock = clock;
    }

    @Operation(summary = "Get current clock status (real vs simulated)")
    @GetMapping
    public ClockStatus status() {
        return clock.status();
    }

    @Operation(summary = "Set a custom simulated date and time")
    @PostMapping("/simulate")
    public ClockStatus simulate(@Valid @RequestBody SetSimulatedTimeRequest request) {
        return clock.setSimulated(request.dateTime(), request.frozen());
    }

    @Operation(summary = "Advance simulated time by days and/or hours")
    @PostMapping("/advance")
    public ClockStatus advance(@RequestBody AdvanceTimeRequest request) {
        return clock.advance(request.days(), request.hours());
    }

    @Operation(summary = "Reset to real system time")
    @PostMapping("/reset")
    public ClockStatus reset() {
        return clock.useRealTime();
    }
}
