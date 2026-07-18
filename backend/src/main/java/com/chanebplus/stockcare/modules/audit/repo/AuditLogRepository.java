package com.chanebplus.stockcare.modules.audit.repo;

import com.chanebplus.stockcare.modules.audit.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByOrderByOccurredAtDesc(Pageable pageable);
}
