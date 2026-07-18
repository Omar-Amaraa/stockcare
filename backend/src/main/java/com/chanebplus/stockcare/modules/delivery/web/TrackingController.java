package com.chanebplus.stockcare.modules.delivery.web;

import com.chanebplus.stockcare.modules.delivery.dto.TrackingPositionDto;
import com.chanebplus.stockcare.modules.delivery.service.DeliveryService;
import com.chanebplus.stockcare.modules.delivery.service.TrackingBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live delivery tracking. SSE stream + REST position history. Access is enforced per delivery. */
@Tag(name = "Delivery tracking")
@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final DeliveryService deliveryService;
    private final TrackingBroadcaster broadcaster;

    public TrackingController(DeliveryService deliveryService, TrackingBroadcaster broadcaster) {
        this.deliveryService = deliveryService;
        this.broadcaster = broadcaster;
    }

    @Operation(summary = "Subscribe to live tracking updates for a delivery (Server-Sent Events)")
    @GetMapping(value = "/deliveries/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        deliveryService.get(id); // enforces visibility (pharmacy sees only its own deliveries)
        return broadcaster.subscribe(id);
    }

    @GetMapping("/deliveries/{id}/positions")
    public List<TrackingPositionDto> positions(@PathVariable UUID id) {
        return deliveryService.positions(id);
    }
}
