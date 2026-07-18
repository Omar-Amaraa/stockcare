package com.chanebplus.stockcare.common.stream;

import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Single workflow event stream for the authenticated user's channel (pharmacy or depot). */
@Tag(name = "Workflow stream")
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final WorkflowBroadcaster broadcaster;

    public StreamController(WorkflowBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Operation(summary = "Subscribe to live workflow updates (SSE) for the current user's channel")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Role role = SecurityUtils.currentRole();
        return switch (role) {
            case PHARMACY -> broadcaster.subscribe(SecurityUtils.currentPharmacyId());
            case DEPOT -> broadcaster.subscribe(SecurityUtils.currentDepotId());
            default -> new SseEmitter(0L);
        };
    }
}
