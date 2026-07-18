package com.chanebplus.stockcare.modules.notification.service;

import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.notification.domain.Notification;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.dto.NotificationDto;
import com.chanebplus.stockcare.modules.notification.repo.NotificationRepository;
import com.chanebplus.stockcare.modules.user.domain.User;
import com.chanebplus.stockcare.modules.user.repo.UserRepository;
import com.chanebplus.stockcare.security.SecurityUtils;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates in-app notifications. The recipient-resolution + persistence here is the seam where future
 * email / SMS / push adapters would additionally fan out each notification.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notifyPharmacy(UUID pharmacyId, NotificationType type, String title, String message,
                               UUID refId, String refType) {
        fanOut(userRepository.findByPharmacyId(pharmacyId), type, title, message, refId, refType);
    }

    @Transactional
    public void notifyDepot(UUID depotId, NotificationType type, String title, String message,
                            UUID refId, String refType) {
        fanOut(userRepository.findByDepotId(depotId), type, title, message, refId, refType);
    }

    private void fanOut(List<User> recipients, NotificationType type, String title, String message,
                        UUID refId, String refType) {
        for (User u : recipients) {
            Notification n = new Notification();
            n.setRecipientUserId(u.getId());
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message);
            n.setReferenceId(refId);
            n.setReferenceType(refType);
            repository.save(n);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> myNotifications(Pageable pageable) {
        return repository.findByRecipientUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId(), pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long myUnreadCount() {
        return repository.countByRecipientUserIdAndReadFalse(SecurityUtils.currentUserId());
    }

    @Transactional
    public NotificationDto markRead(UUID id) {
        Notification n = repository.findById(id).orElseThrow(() -> NotFoundException.of("Notification", id));
        if (!n.getRecipientUserId().equals(SecurityUtils.currentUserId())) {
            throw new com.chanebplus.stockcare.common.error.ForbiddenAccessException("Not your notification");
        }
        n.setRead(true);
        return toDto(repository.save(n));
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.isRead(), n.getReferenceId(), n.getReferenceType(), n.getCreatedAt());
    }
}
