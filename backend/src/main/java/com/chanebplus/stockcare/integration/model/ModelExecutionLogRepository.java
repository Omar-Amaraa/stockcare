package com.chanebplus.stockcare.integration.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelExecutionLogRepository extends JpaRepository<ModelExecutionLog, UUID> {
}
