package com.chanebplus.stockcare.modules.audit.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Append-only record of noteworthy actions for the audit-history view. */
@Getter
@Setter
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = 1000)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
