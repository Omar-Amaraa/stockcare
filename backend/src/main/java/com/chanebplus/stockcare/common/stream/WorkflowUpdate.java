package com.chanebplus.stockcare.common.stream;

import java.time.Instant;
import java.util.UUID;

/** Payload pushed over the workflow SSE stream so the UI updates without a page reload. */
public record WorkflowUpdate(
        String type,        // PREDICTION_STATE, REQUEST_STATE, PRIORITY_STATE, DELIVERY_STATE
        String entityType,  // PHARMACY, REQUEST, DELIVERY
        UUID entityId,
        String status,
        String message,
        Instant at) {

    public static WorkflowUpdate of(String type, String entityType, UUID entityId, String status, String message) {
        return new WorkflowUpdate(type, entityType, entityId, status, message, Instant.now());
    }
}
