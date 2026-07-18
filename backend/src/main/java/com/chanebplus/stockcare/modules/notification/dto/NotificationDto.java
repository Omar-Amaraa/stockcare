package com.chanebplus.stockcare.modules.notification.dto;

import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        UUID referenceId,
        String referenceType,
        Instant createdAt) {}
