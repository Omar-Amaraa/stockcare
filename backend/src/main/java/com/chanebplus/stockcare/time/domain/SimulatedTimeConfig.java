package com.chanebplus.stockcare.time.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted single-row configuration for the application clock. When mode is SIMULATED, the
 * effective time is derived from the simulated anchor plus the real elapsed time (unless frozen).
 */
@Getter
@Setter
@Entity
@Table(name = "simulated_time_config")
public class SimulatedTimeConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClockMode mode = ClockMode.REAL;

    /** Simulated instant that corresponds to {@link #realAnchor}. */
    @Column(name = "simulated_anchor")
    private Instant simulatedAnchor;

    /** Real instant captured when the simulated time was configured. */
    @Column(name = "real_anchor")
    private Instant realAnchor;

    /** If true, simulated time is frozen at {@link #simulatedAnchor} and does not advance. */
    @Column(name = "frozen", nullable = false)
    private boolean frozen = false;
}
