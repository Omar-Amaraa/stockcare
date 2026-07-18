package com.chanebplus.stockcare.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chanebplus.stockcare.time.domain.ClockMode;
import com.chanebplus.stockcare.time.domain.SimulatedTimeConfig;
import com.chanebplus.stockcare.time.domain.SimulatedTimeConfigRepository;
import com.chanebplus.stockcare.time.service.ApplicationClockService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationClockServiceTest {

    @Mock
    SimulatedTimeConfigRepository repository;

    @Mock
    org.springframework.context.ApplicationEventPublisher events;

    ApplicationClockService clock;
    SimulatedTimeConfig config;

    @BeforeEach
    void setUp() {
        config = new SimulatedTimeConfig();
        config.setMode(ClockMode.REAL);
        when(repository.findAll()).thenReturn(List.of(config));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        clock = new ApplicationClockService(repository, events);
    }

    @Test
    void realModeReturnsSystemTime() {
        assertThat(clock.now()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    void frozenSimulatedTimeReturnsExactAnchor() {
        Instant target = Instant.parse("2030-06-15T12:00:00Z");
        clock.setSimulated(target, true);
        assertThat(clock.now()).isEqualTo(target);
        assertThat(clock.status().simulationActive()).isTrue();
    }

    @Test
    void resetReturnsToRealMode() {
        clock.setSimulated(Instant.parse("2030-06-15T12:00:00Z"), true);
        clock.useRealTime();
        assertThat(clock.status().mode()).isEqualTo(ClockMode.REAL);
    }
}
