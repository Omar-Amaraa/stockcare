package com.chanebplus.stockcare.modules.route.workflow;

import com.chanebplus.stockcare.common.stream.WorkflowBroadcaster;
import com.chanebplus.stockcare.common.stream.WorkflowUpdate;
import com.chanebplus.stockcare.config.WorkflowProperties;
import com.chanebplus.stockcare.modules.delivery.dto.FleetPlanRequest;
import com.chanebplus.stockcare.modules.delivery.dto.FleetPlanResult;
import com.chanebplus.stockcare.modules.delivery.repo.DeliveryRepository;
import com.chanebplus.stockcare.modules.delivery.service.DeliveryService;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Closes the automated loop: once requests are approved for planning, the depot's whole backlog is
 * handed to the MILP optimizer without a manual "plan route" action.
 *
 * <p>The chain is prediction → draft request → automatic priority → approval → <b>this step</b> →
 * deliveries. Enabled with {@code stockcare.workflow.auto-route=true}.
 *
 * <p>Approving several requests fires several events, so runs are serialised per depot and each run
 * re-reads the backlog, skipping anything already committed to a delivery. Concurrent events
 * therefore collapse into one plan instead of producing duplicate deliveries.
 */
@Service
public class RouteWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RouteWorkflowService.class);

    private final PharmacyRequestRepository requestRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final NotificationService notifications;
    private final WorkflowBroadcaster broadcaster;
    private final WorkflowProperties workflow;

    private final Map<UUID, ReentrantLock> depotLocks = new ConcurrentHashMap<>();

    public RouteWorkflowService(PharmacyRequestRepository requestRepository,
                                DeliveryRepository deliveryRepository,
                                DeliveryService deliveryService,
                                NotificationService notifications,
                                WorkflowBroadcaster broadcaster,
                                WorkflowProperties workflow) {
        this.requestRepository = requestRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryService = deliveryService;
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.workflow = workflow;
    }

    /** Plans every approved-but-unplanned request of a depot in a single fleet-wide solve. */
    public void autoPlan(UUID depotId) {
        if (!workflow.isAutoRoute() || depotId == null) {
            return;
        }
        ReentrantLock lock = depotLocks.computeIfAbsent(depotId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            // Another approval from the same wave is already planning; its run will pick this up.
            log.debug("Auto-route for depot {} already running, skipping duplicate trigger", depotId);
            return;
        }
        try {
            runPlan(depotId);
        } finally {
            lock.unlock();
        }
    }

    /** Not transactional on purpose: each step below manages its own transaction, so a failed
     *  solve never rolls back the deliveries already written by a previous wave. */
    private void runPlan(UUID depotId) {
        List<UUID> pending = pendingRequestIds(depotId);
        if (pending.size() < Math.max(1, workflow.getAutoRouteMinRequests())) {
            log.debug("Auto-route for depot {}: {} pending request(s), below threshold {}",
                    depotId, pending.size(), workflow.getAutoRouteMinRequests());
            return;
        }

        emit(depotId, "OPTIMIZING", "Optimizing delivery routes for " + pending.size() + " request(s)…");
        try {
            FleetPlanResult result = deliveryService.plan(depotId,
                    new FleetPlanRequest(pending, List.<UUID>of(), List.<UUID>of(), null));

            emit(depotId, "PLANNED", result.deliveries().size() + " delivery route(s) planned automatically");
            log.info("Auto-route depot {}: {} delivery(ies), status={}, unfulfilled={}",
                    depotId, result.deliveries().size(), result.status(),
                    result.unfulfilledRequestIds().size());

            if (!result.unfulfilledRequestIds().isEmpty()) {
                notifications.notifyDepot(depotId, NotificationType.ROUTE_PLANNED,
                        "Some requests could not be loaded",
                        result.unfulfilledRequestIds().size() + " approved request(s) did not fit the available "
                                + "fleet and remain unplanned. " + result.note(), depotId, "DEPOT");
            }
        } catch (RuntimeException ex) {
            log.warn("Auto-route failed for depot {}: {}", depotId, ex.getMessage());
            emit(depotId, "FAILED", "Automatic route planning failed: " + ex.getMessage());
            notifications.notifyDepot(depotId, NotificationType.ROUTE_PLANNED,
                    "Automatic planning failed",
                    "Route optimization could not complete: " + ex.getMessage()
                            + " The requests remain approved and can be planned manually.",
                    depotId, "DEPOT");
        }
    }

    /** Approved requests of the depot that are not already committed to a delivery. */
    private List<UUID> pendingRequestIds(UUID depotId) {
        Set<UUID> alreadyPlanned = new HashSet<>(deliveryRepository.findPlannedRequestIds(depotId));
        List<UUID> pending = new ArrayList<>();
        for (PharmacyRequest r : requestRepository
                .findByDepotIdAndStatusOrderByCreatedAtAsc(depotId, RequestStatus.PLANNED)) {
            if (!alreadyPlanned.contains(r.getId())) {
                pending.add(r.getId());
            }
        }
        return pending;
    }

    private void emit(UUID depotId, String status, String message) {
        broadcaster.publish(depotId, WorkflowUpdate.of("ROUTE_STATE", "DEPOT", depotId, status, message));
    }
}
