package com.chanebplus.stockcare.modules.request.workflow;

import com.chanebplus.stockcare.common.events.WorkflowEvents;
import com.chanebplus.stockcare.common.stream.WorkflowBroadcaster;
import com.chanebplus.stockcare.common.stream.WorkflowUpdate;
import com.chanebplus.stockcare.config.WorkflowProperties;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionResult;
import com.chanebplus.stockcare.modules.prediction.repo.PredictionResultRepository;
import com.chanebplus.stockcare.modules.priority.service.PriorityService;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automated request-side workflow: priority is calculated automatically through the lifecycle
 * (SUBMITTED → RECEIVED → PRIORITY_PENDING → PRIORITIZED) and shortages can auto-generate draft
 * requests. No manual "calculate priority" action is required.
 */
@Service
public class RequestWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RequestWorkflowService.class);
    private static final List<RequestStatus> OPEN = List.of(
            RequestStatus.DRAFT, RequestStatus.SUBMITTED, RequestStatus.RECEIVED,
            RequestStatus.PRIORITY_PENDING, RequestStatus.PRIORITIZED, RequestStatus.PLANNED,
            RequestStatus.PREPARING, RequestStatus.IN_DELIVERY);

    private final PharmacyRequestRepository requestRepository;
    private final PredictionResultRepository predictionRepository;
    private final DepotPharmacyRepository depotPharmacyRepository;
    private final PriorityService priorityService;
    private final NotificationService notifications;
    private final WorkflowBroadcaster broadcaster;
    private final WorkflowProperties workflow;
    private final ApplicationEventPublisher events;
    private final ApplicationClock clock;

    public RequestWorkflowService(PharmacyRequestRepository requestRepository,
                                  PredictionResultRepository predictionRepository,
                                  DepotPharmacyRepository depotPharmacyRepository,
                                  PriorityService priorityService, NotificationService notifications,
                                  WorkflowBroadcaster broadcaster, WorkflowProperties workflow,
                                  ApplicationEventPublisher events, ApplicationClock clock) {
        this.requestRepository = requestRepository;
        this.predictionRepository = predictionRepository;
        this.depotPharmacyRepository = depotPharmacyRepository;
        this.priorityService = priorityService;
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.workflow = workflow;
        this.events = events;
        this.clock = clock;
    }

    /** Automatically prioritize a freshly submitted request. */
    @Transactional
    public void autoPrioritize(UUID requestId) {
        if (!workflow.isAutoPriority()) {
            return;
        }
        PharmacyRequest r = requestRepository.findWithItemsById(requestId).orElse(null);
        if (r == null || r.getStatus() != RequestStatus.SUBMITTED) {
            return;
        }
        UUID pid = r.getPharmacy().getId();
        UUID did = r.getDepot().getId();

        r.setStatus(RequestStatus.RECEIVED);
        requestRepository.saveAndFlush(r);
        emit(pid, did, "REQUEST_STATE", requestId, "RECEIVED", "Request received by depot");

        r.setStatus(RequestStatus.PRIORITY_PENDING);
        requestRepository.saveAndFlush(r);
        emit(pid, did, "PRIORITY_STATE", requestId, "CALCULATING", "Calculating priority…");

        try {
            var result = priorityService.calculateFor(r);
            r.setStatus(RequestStatus.PRIORITIZED);
            requestRepository.save(r);
            emit(pid, did, "PRIORITY_STATE", requestId, "CALCULATED",
                    "Priority " + result.coefficient() + "/100");
            notifications.notifyPharmacy(pid, NotificationType.PRIORITY_CALCULATED, "Priority calculated",
                    "Depot computed a priority of " + result.coefficient() + "/100 for your request.",
                    requestId, "REQUEST");
            events.publishEvent(new WorkflowEvents.RequestPrioritized(requestId, did));
        } catch (RuntimeException ex) {
            log.warn("Auto priority failed for {}: {}", requestId, ex.getMessage());
            r.setStatus(RequestStatus.RECEIVED);
            requestRepository.save(r);
            emit(pid, did, "PRIORITY_STATE", requestId, "FAILED", "Priority calculation failed");
        }
    }

    /** Create draft requests from a pharmacy's predicted shortages, avoiding duplicates. */
    @Transactional
    public void autoDraftForShortages(UUID pharmacyId) {
        if (!"draft".equalsIgnoreCase(workflow.getShortageAction())) {
            return; // "alert" already notified during prediction; "off" does nothing
        }
        Depot depot = depotPharmacyRepository.findFirstByPharmacyId(pharmacyId)
                .map(link -> link.getDepot()).orElse(null);
        if (depot == null) {
            return;
        }
        List<PredictionResult> shortages =
                predictionRepository.findByPharmacyIdOrderByPredictedShortageDateAsc(pharmacyId);
        int created = 0;
        for (PredictionResult p : shortages) {
            UUID medId = p.getMedication().getId();
            if (requestRepository.existsOpenForMedication(pharmacyId, medId, OPEN)) {
                continue; // duplicate prevention
            }
            PharmacyRequest r = new PharmacyRequest();
            r.setPharmacy(p.getPharmacy());
            r.setDepot(depot);
            r.setStatus(RequestStatus.DRAFT);
            r.setUrgency(urgencyFor(p.getEstimatedRemainingDays()));
            r.setNotes("Auto-drafted from predicted shortage on " + p.getPredictedShortageDate());
            r.setSourcePredictionId(p.getId());
            PharmacyRequestItem item = new PharmacyRequestItem();
            item.setMedication(p.getMedication());
            item.setRequestedQuantity(Math.max(1, p.getPredictedMissingQuantity()));
            item.setNote("Predicted shortfall");
            r.addItem(item);

            if (workflow.isAutoSubmit()) {
                r.setStatus(RequestStatus.SUBMITTED);
                r.setSubmittedAt(clock.now());
            }
            PharmacyRequest saved = requestRepository.save(r);
            created++;
            if (workflow.isAutoSubmit()) {
                notifications.notifyDepot(depot.getId(), NotificationType.NEW_REQUEST, "New pharmacy request",
                        saved.getPharmacy().getName() + " submitted an auto-generated request.", saved.getId(), "REQUEST");
                events.publishEvent(new WorkflowEvents.RequestSubmitted(saved.getId()));
            }
            emit(pharmacyId, depot.getId(), "REQUEST_STATE", saved.getId(), saved.getStatus().name(),
                    "Draft request created from prediction");
        }
        if (created > 0) {
            notifications.notifyPharmacy(pharmacyId, NotificationType.SHORTAGE_PREDICTED, "Draft requests ready",
                    created + " draft request(s) created from predicted shortages — review and submit.",
                    pharmacyId, "PHARMACY");
        }
    }

    private Urgency urgencyFor(Integer remainingDays) {
        if (remainingDays == null) return Urgency.NORMAL;
        if (remainingDays <= 3) return Urgency.CRITICAL;
        if (remainingDays <= 7) return Urgency.HIGH;
        if (remainingDays <= 14) return Urgency.NORMAL;
        return Urgency.LOW;
    }

    private void emit(UUID pharmacyId, UUID depotId, String type, UUID id, String status, String message) {
        WorkflowUpdate u = WorkflowUpdate.of(type, "REQUEST", id, status, message);
        broadcaster.publish(pharmacyId, u);
        broadcaster.publish(depotId, u);
    }
}
