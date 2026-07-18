package com.chanebplus.stockcare.common.stream;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out of {@link WorkflowUpdate}s to subscribers, keyed by "channel" (a pharmacy id or a depot
 * id). The frontend opens one SSE connection for its channel and refreshes affected views.
 */
@Component
public class WorkflowBroadcaster {

    private final Map<UUID, List<SseEmitter>> channels = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID channelId) {
        SseEmitter emitter = new SseEmitter(0L);
        channels.computeIfAbsent(channelId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(channelId, emitter));
        emitter.onTimeout(() -> remove(channelId, emitter));
        emitter.onError(e -> remove(channelId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(channelId.toString()));
        } catch (IOException ignored) {
        }
        return emitter;
    }

    public void publish(UUID channelId, WorkflowUpdate update) {
        if (channelId == null) {
            return;
        }
        List<SseEmitter> list = channels.get(channelId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("workflow").data(update));
            } catch (IOException e) {
                remove(channelId, emitter);
            }
        }
    }

    private void remove(UUID channelId, SseEmitter emitter) {
        List<SseEmitter> list = channels.get(channelId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
