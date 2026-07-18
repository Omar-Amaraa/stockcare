package com.chanebplus.stockcare.modules.audit.service;

import com.chanebplus.stockcare.modules.audit.domain.AuditLog;
import com.chanebplus.stockcare.modules.audit.repo.AuditLogRepository;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ApplicationClock clock;

    public AuditService(AuditLogRepository repository, ApplicationClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, UUID entityId, String detail) {
        AuditLog logEntry = new AuditLog();
        logEntry.setActor(currentActor());
        logEntry.setAction(action);
        logEntry.setEntityType(entityType);
        logEntry.setEntityId(entityId);
        logEntry.setDetail(detail);
        logEntry.setOccurredAt(clock.now());
        repository.save(logEntry);
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
