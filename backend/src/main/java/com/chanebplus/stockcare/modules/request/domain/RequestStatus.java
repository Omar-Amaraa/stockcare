package com.chanebplus.stockcare.modules.request.domain;

/** Lifecycle of a pharmacy restock request. */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    RECEIVED,
    PRIORITY_PENDING,
    PRIORITIZED,
    PLANNED,
    PREPARING,
    IN_DELIVERY,
    DELIVERED,
    CANCELLED,
    REJECTED;

    /** Statuses a pharmacy may still cancel from (not yet in fulfilment). */
    public boolean isCancellable() {
        return this == DRAFT || this == SUBMITTED || this == RECEIVED;
    }
}
