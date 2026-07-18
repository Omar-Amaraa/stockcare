package com.chanebplus.stockcare.modules.notification.repo;

import com.chanebplus.stockcare.modules.notification.domain.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);
    long countByRecipientUserIdAndReadFalse(UUID recipientUserId);
}
