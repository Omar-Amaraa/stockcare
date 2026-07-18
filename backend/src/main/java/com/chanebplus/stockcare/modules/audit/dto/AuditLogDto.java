package com.chanebplus.stockcare.modules.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(UUID id, String actor, String action, String entityType,
                          UUID entityId, String detail, Instant occurredAt) {}
