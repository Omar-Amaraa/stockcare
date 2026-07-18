package com.chanebplus.stockcare.time.dto;

import com.chanebplus.stockcare.time.domain.ClockMode;
import java.time.Instant;

/** Current state of the application clock, surfaced to the UI so simulated time is visible. */
public record ClockStatus(
        ClockMode mode,
        boolean simulationActive,
        boolean frozen,
        Instant effectiveNow,
        Instant realNow) {}
