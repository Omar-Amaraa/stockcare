package com.chanebplus.stockcare.time.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Central time abstraction. All business logic must obtain "now" from this instead of calling
 * the system clock directly, so the simulated-time controls affect predictions, requests,
 * deliveries and audit timestamps consistently.
 */
public interface ApplicationClock {

    ZoneId ZONE = ZoneId.of("Africa/Tunis");

    /** Current effective instant (real or simulated). */
    Instant now();

    default LocalDateTime nowDateTime() {
        return LocalDateTime.ofInstant(now(), ZONE);
    }

    default LocalDate today() {
        return nowDateTime().toLocalDate();
    }
}
