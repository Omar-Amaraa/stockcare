package com.chanebplus.stockcare.modules.audit.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.audit.dto.AuditLogDto;
import com.chanebplus.stockcare.modules.audit.repo.AuditLogRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit history")
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('DEPOT','ADMIN')")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<AuditLogDto> list(@PageableDefault(size = 30) Pageable pageable) {
        return PageResponse.from(repository.findByOrderByOccurredAtDesc(pageable)
                .map(a -> new AuditLogDto(a.getId(), a.getActor(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getDetail(), a.getOccurredAt())));
    }
}
