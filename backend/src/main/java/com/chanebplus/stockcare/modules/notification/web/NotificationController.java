package com.chanebplus.stockcare.modules.notification.web;

import com.chanebplus.stockcare.common.api.PageResponse;
import com.chanebplus.stockcare.modules.notification.dto.NotificationDto;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationDto> mine(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.myNotifications(pageable));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", service.myUnreadCount());
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }
}
