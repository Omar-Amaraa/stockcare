package com.chanebplus.stockcare.modules.priority.repo;

import com.chanebplus.stockcare.modules.priority.domain.PriorityResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityResultRepository extends JpaRepository<PriorityResult, UUID> {
    Optional<PriorityResult> findByRequestId(UUID requestId);
}
