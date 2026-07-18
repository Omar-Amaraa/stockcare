package com.chanebplus.stockcare.time.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulatedTimeConfigRepository extends JpaRepository<SimulatedTimeConfig, UUID> {
}
