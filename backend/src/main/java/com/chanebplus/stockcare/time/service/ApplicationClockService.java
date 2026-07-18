package com.chanebplus.stockcare.time.service;

import com.chanebplus.stockcare.time.api.ApplicationClock;
import com.chanebplus.stockcare.time.domain.ClockMode;
import com.chanebplus.stockcare.time.domain.SimulatedTimeConfig;
import com.chanebplus.stockcare.time.domain.SimulatedTimeConfigRepository;
import com.chanebplus.stockcare.common.events.WorkflowEvents;
import com.chanebplus.stockcare.time.dto.ClockStatus;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ApplicationClock} on top of a persisted configuration row so simulated time
 * survives restarts in the test environment.
 */
@Service
public class ApplicationClockService implements ApplicationClock {

    private final SimulatedTimeConfigRepository repository;
    private final ApplicationEventPublisher events;

    public ApplicationClockService(SimulatedTimeConfigRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public Instant now() {
        return effectiveNow(currentConfig());
    }

    @Transactional(readOnly = true)
    public ClockStatus status() {
        SimulatedTimeConfig config = currentConfig();
        boolean simulated = config.getMode() == ClockMode.SIMULATED;
        return new ClockStatus(config.getMode(), simulated, config.isFrozen(),
                effectiveNow(config), Instant.now());
    }

    @Transactional
    public ClockStatus useRealTime() {
        SimulatedTimeConfig config = currentConfig();
        config.setMode(ClockMode.REAL);
        config.setSimulatedAnchor(null);
        config.setRealAnchor(null);
        config.setFrozen(false);
        repository.save(config);
        events.publishEvent(new WorkflowEvents.SimulatedTimeChanged());
        return status();
    }

    @Transactional
    public ClockStatus setSimulated(Instant target, boolean frozen) {
        SimulatedTimeConfig config = currentConfig();
        config.setMode(ClockMode.SIMULATED);
        config.setSimulatedAnchor(target);
        config.setRealAnchor(Instant.now());
        config.setFrozen(frozen);
        repository.save(config);
        events.publishEvent(new WorkflowEvents.SimulatedTimeChanged());
        return status();
    }

    @Transactional
    public ClockStatus advance(long days, long hours) {
        SimulatedTimeConfig config = currentConfig();
        Instant base = effectiveNow(config);
        Instant target = base.plus(Duration.ofDays(days).plusHours(hours));
        boolean frozen = config.getMode() == ClockMode.SIMULATED && config.isFrozen();
        return setSimulated(target, frozen);
    }

    private Instant effectiveNow(SimulatedTimeConfig config) {
        if (config.getMode() == ClockMode.REAL || config.getSimulatedAnchor() == null) {
            return Instant.now();
        }
        if (config.isFrozen()) {
            return config.getSimulatedAnchor();
        }
        Duration elapsed = Duration.between(config.getRealAnchor(), Instant.now());
        return config.getSimulatedAnchor().plus(elapsed);
    }

    private SimulatedTimeConfig currentConfig() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            SimulatedTimeConfig fresh = new SimulatedTimeConfig();
            fresh.setMode(ClockMode.REAL);
            return repository.save(fresh);
        });
    }
}
