package com.chanebplus.stockcare.modules.delivery.service;

import com.chanebplus.stockcare.modules.delivery.dto.TrackingUpdate;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Holds per-delivery SSE subscribers and pushes live tracking updates to them. */
@Component
public class TrackingBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrackingBroadcaster.class);
    private final Map<UUID, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID deliveryId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        subscribers.computeIfAbsent(deliveryId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(deliveryId, emitter));
        emitter.onTimeout(() -> remove(deliveryId, emitter));
        emitter.onError(e -> remove(deliveryId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(deliveryId.toString()));
        } catch (IOException ignored) {
        }
        return emitter;
    }

    public void broadcast(UUID deliveryId, TrackingUpdate update) {
        List<SseEmitter> list = subscribers.get(deliveryId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("tracking").data(update));
            } catch (IOException e) {
                remove(deliveryId, emitter);
            }
        }
    }

    private void remove(UUID deliveryId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(deliveryId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
