package com.chanebplus.stockcare.modules.request.workflow;

import com.chanebplus.stockcare.common.events.WorkflowEvents;
import com.chanebplus.stockcare.modules.pharmacy.repo.PharmacyRepository;
import com.chanebplus.stockcare.modules.prediction.service.PredictionService;
import com.chanebplus.stockcare.modules.route.workflow.RouteWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Wires domain events to the automated workflow. Listeners run AFTER_COMMIT (so the triggering data
 * is visible) and asynchronously (so the API call returns immediately). Each delegates to a
 * transactional service method.
 */
@Component
public class WorkflowListeners {

    private static final Logger log = LoggerFactory.getLogger(WorkflowListeners.class);

    private final PredictionService predictionService;
    private final RequestWorkflowService requestWorkflow;
    private final RouteWorkflowService routeWorkflow;
    private final PharmacyRepository pharmacyRepository;

    public WorkflowListeners(PredictionService predictionService, RequestWorkflowService requestWorkflow,
                             RouteWorkflowService routeWorkflow, PharmacyRepository pharmacyRepository) {
        this.predictionService = predictionService;
        this.requestWorkflow = requestWorkflow;
        this.routeWorkflow = routeWorkflow;
        this.pharmacyRepository = pharmacyRepository;
    }

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventoryChanged(WorkflowEvents.InventoryChanged e) {
        runPrediction(e.pharmacyId(), false);
    }

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSimulatedTimeChanged(WorkflowEvents.SimulatedTimeChanged e) {
        pharmacyRepository.findAll().forEach(p -> runPrediction(p.getId(), true));
    }

    /** begin (commits PROCESSING) then execute (commits result) so the state is observable. */
    private void runPrediction(java.util.UUID pharmacyId, boolean force) {
        safe(() -> {
            String cid = predictionService.beginRun(pharmacyId, force);
            if (cid != null) {
                predictionService.executeRun(pharmacyId, cid);
            }
        }, "prediction run");
    }

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestSubmitted(WorkflowEvents.RequestSubmitted e) {
        safe(() -> requestWorkflow.autoPrioritize(e.requestId()), "auto prioritize");
    }

    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShortagePredicted(WorkflowEvents.ShortagePredicted e) {
        safe(() -> requestWorkflow.autoDraftForShortages(e.pharmacyId()), "auto draft");
    }

    /** Final automated step: approved requests are routed by the MILP optimizer. */
    @Async("workflowExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestApprovedForPlanning(WorkflowEvents.RequestApprovedForPlanning e) {
        safe(() -> routeWorkflow.autoPlan(e.depotId()), "auto route");
    }

    private void safe(Runnable r, String what) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            log.warn("Workflow step '{}' failed: {}", what, ex.getMessage());
        }
    }
}
