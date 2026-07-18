package com.chanebplus.stockcare.common.events;

import java.util.UUID;

/** Domain events that drive the automated, event-driven workflows (Spring application events). */
public final class WorkflowEvents {

    private WorkflowEvents() {}

    /** A pharmacy's inventory changed (item created/updated/deleted or stock adjusted). */
    public record InventoryChanged(UUID pharmacyId) {}

    /** The simulated application clock changed (affects every pharmacy's forecast context). */
    public record SimulatedTimeChanged() {}

    /** A prediction run completed and found one or more shortages. */
    public record ShortagePredicted(UUID pharmacyId) {}

    /** A pharmacy request was submitted to the depot. */
    public record RequestSubmitted(UUID requestId) {}

    /** A factor affecting a request's priority changed; the score must be recalculated. */
    public record PriorityFactorsChanged(UUID requestId) {}

    /** A request became prioritized (route-optimization readiness signal). */
    public record RequestPrioritized(UUID requestId, UUID depotId) {}
}
