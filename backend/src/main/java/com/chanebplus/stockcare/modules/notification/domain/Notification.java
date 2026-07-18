package com.chanebplus.stockcare.modules.notification.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** In-app notification for a single recipient user. Adapter layer can later fan out to email/SMS/push. */
@Getter
@Setter
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "read_flag", nullable = false)
    private boolean read = false;

    /** Optional reference to the related entity (requestId / deliveryId) for deep-linking. */
    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 32)
    private String referenceType;
}
