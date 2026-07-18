package com.chanebplus.stockcare.modules.request.service;

import com.chanebplus.stockcare.common.error.BusinessRuleException;
import com.chanebplus.stockcare.common.error.ForbiddenAccessException;
import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.depot.repo.DepotPharmacyRepository;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionResult;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.audit.service.AuditService;
import com.chanebplus.stockcare.modules.prediction.repo.PredictionResultRepository;
import com.chanebplus.stockcare.modules.priority.service.PriorityService;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequestItem;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import com.chanebplus.stockcare.modules.request.dto.ChangeStatusDto;
import com.chanebplus.stockcare.modules.request.dto.CreateRequestDto;
import com.chanebplus.stockcare.modules.request.dto.DraftFromPredictionDto;
import com.chanebplus.stockcare.modules.request.dto.PriorityResultDto;
import com.chanebplus.stockcare.modules.request.dto.RequestDto;
import com.chanebplus.stockcare.modules.request.dto.RequestItemInput;
import com.chanebplus.stockcare.modules.request.repo.PharmacyRequestRepository;
import com.chanebplus.stockcare.security.SecurityUtils;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pharmacy request workflow (both pharmacy and depot sides). Business rules live here rather than
 * in controllers, and every operation enforces role-based ownership.
 */
@Service
public class RequestService {

    private final PharmacyRequestRepository requestRepository;
    private final DepotPharmacyRepository depotPharmacyRepository;
    private final PredictionResultRepository predictionRepository;
    private final PharmacyAccessService accessService;
    private final PriorityService priorityService;
    private final RequestAssembler assembler;
    private final ApplicationClock clock;
    private final EntityManager em;
    private final NotificationService notifications;
    private final AuditService audit;
    private final org.springframework.context.ApplicationEventPublisher events;

    public RequestService(PharmacyRequestRepository requestRepository,
                          DepotPharmacyRepository depotPharmacyRepository,
                          PredictionResultRepository predictionRepository,
                          PharmacyAccessService accessService,
                          PriorityService priorityService,
                          RequestAssembler assembler,
                          ApplicationClock clock,
                          EntityManager em,
                          NotificationService notifications,
                          AuditService audit,
                          org.springframework.context.ApplicationEventPublisher events) {
        this.requestRepository = requestRepository;
        this.depotPharmacyRepository = depotPharmacyRepository;
        this.predictionRepository = predictionRepository;
        this.accessService = accessService;
        this.priorityService = priorityService;
        this.assembler = assembler;
        this.clock = clock;
        this.em = em;
        this.notifications = notifications;
        this.audit = audit;
        this.events = events;
    }

    // ---------------- Pharmacy side ----------------

    @Transactional
    public RequestDto createDraft(UUID pharmacyId, CreateRequestDto dto) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        Depot depot = resolveDepot(pharmacyId);
        PharmacyRequest request = new PharmacyRequest();
        request.setPharmacy(em.getReference(com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy.class, pharmacyId));
        request.setDepot(depot);
        request.setStatus(RequestStatus.DRAFT);
        request.setUrgency(dto.urgency());
        request.setNotes(dto.notes());
        request.setAffectedPatients(dto.affectedPatients());
        for (RequestItemInput in : dto.items()) {
            request.addItem(newItem(in));
        }
        return assembler.toDto(requestRepository.save(request));
    }

    @Transactional
    public RequestDto draftFromPrediction(UUID pharmacyId, DraftFromPredictionDto dto) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        PredictionResult prediction = predictionRepository.findById(dto.predictionId())
                .orElseThrow(() -> NotFoundException.of("PredictionResult", dto.predictionId()));
        if (!prediction.getPharmacy().getId().equals(pharmacyId)) {
            throw new ForbiddenAccessException("Prediction does not belong to this pharmacy");
        }
        Depot depot = resolveDepot(pharmacyId);
        int qty = dto.requestedQuantity() != null ? dto.requestedQuantity()
                : Math.max(1, prediction.getPredictedMissingQuantity());

        PharmacyRequest request = new PharmacyRequest();
        request.setPharmacy(prediction.getPharmacy());
        request.setDepot(depot);
        request.setStatus(RequestStatus.DRAFT);
        request.setUrgency(dto.urgency() != null ? dto.urgency() : Urgency.NORMAL);
        request.setNotes(dto.notes() != null ? dto.notes()
                : "Drafted from shortage prediction " + prediction.getId());
        request.setAffectedPatients(dto.affectedPatients());
        request.setSourcePredictionId(prediction.getId());

        PharmacyRequestItem item = new PharmacyRequestItem();
        item.setMedication(prediction.getMedication());
        item.setRequestedQuantity(qty);
        item.setNote("Predicted shortfall around " + prediction.getPredictedShortageDate());
        request.addItem(item);

        return assembler.toDto(requestRepository.save(request));
    }

    @Transactional
    public RequestDto updateDraft(UUID pharmacyId, UUID requestId, CreateRequestDto dto) {
        PharmacyRequest request = loadOwnedByPharmacy(pharmacyId, requestId);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT requests can be edited");
        }
        request.setUrgency(dto.urgency());
        request.setNotes(dto.notes());
        request.setAffectedPatients(dto.affectedPatients());
        request.getItems().clear();
        for (RequestItemInput in : dto.items()) {
            request.addItem(newItem(in));
        }
        return assembler.toDto(requestRepository.save(request));
    }

    @Transactional
    public RequestDto submit(UUID pharmacyId, UUID requestId) {
        PharmacyRequest request = loadOwnedByPharmacy(pharmacyId, requestId);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT requests can be submitted");
        }
        request.setStatus(RequestStatus.SUBMITTED);
        request.setSubmittedAt(clock.now());
        PharmacyRequest saved = requestRepository.save(request);
        notifications.notifyDepot(saved.getDepot().getId(), NotificationType.NEW_REQUEST,
                "New pharmacy request",
                saved.getPharmacy().getName() + " submitted a " + saved.getUrgency() + " request.",
                saved.getId(), "REQUEST");
        audit.record("REQUEST_SUBMITTED", "PharmacyRequest", saved.getId(),
                "urgency=" + saved.getUrgency());
        // Trigger automatic prioritization (depot no longer clicks "calculate priority").
        events.publishEvent(new com.chanebplus.stockcare.common.events.WorkflowEvents.RequestSubmitted(saved.getId()));
        return assembler.toDto(saved);
    }

    @Transactional
    public RequestDto cancel(UUID requestId) {
        PharmacyRequest request = loadWithItems(requestId);
        // Only the owning pharmacy may cancel.
        accessService.assertCanAccessPharmacy(request.getPharmacy().getId());
        if (SecurityUtils.currentRole() == com.chanebplus.stockcare.modules.user.domain.Role.PHARMACY
                && !request.getPharmacy().getId().equals(SecurityUtils.currentPharmacyId())) {
            throw new ForbiddenAccessException("Not your request");
        }
        if (!request.getStatus().isCancellable()) {
            throw new BusinessRuleException("Request can no longer be cancelled (" + request.getStatus() + ")");
        }
        request.setStatus(RequestStatus.CANCELLED);
        return assembler.toDto(requestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public Page<RequestDto> listForPharmacy(UUID pharmacyId, Pageable pageable) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        return requestRepository.findByPharmacyId(pharmacyId, pageable).map(assembler::toDto);
    }

    @Transactional(readOnly = true)
    public RequestDto get(UUID requestId) {
        PharmacyRequest request = loadWithItems(requestId);
        accessService.assertCanAccessPharmacy(request.getPharmacy().getId());
        return assembler.toDto(request);
    }

    // ---------------- Depot side ----------------

    @Transactional(readOnly = true)
    public Page<RequestDto> listForDepot(UUID depotId, RequestStatus status, Urgency urgency,
                                         UUID pharmacyId, Pageable pageable) {
        assertDepot(depotId);
        return requestRepository.findForDepot(depotId, status, urgency, pharmacyId, pageable)
                .map(assembler::toDto);
    }

    @Transactional
    public RequestDto changeStatus(UUID requestId, ChangeStatusDto dto) {
        PharmacyRequest request = loadWithItems(requestId);
        assertDepot(request.getDepot().getId());
        request.setStatus(dto.status());
        if (dto.status() == RequestStatus.SUBMITTED && request.getSubmittedAt() == null) {
            request.setSubmittedAt(clock.now());
        }
        PharmacyRequest saved = requestRepository.save(request);
        notifications.notifyPharmacy(saved.getPharmacy().getId(), NotificationType.REQUEST_STATUS_UPDATED,
                "Request status updated",
                "Your request is now " + saved.getStatus() + ".", saved.getId(), "REQUEST");
        return assembler.toDto(saved);
    }

    @Transactional
    public RequestDto addInternalNote(UUID requestId, String note) {
        PharmacyRequest request = loadWithItems(requestId);
        assertDepot(request.getDepot().getId());
        request.setInternalNotes(note);
        return assembler.toDto(requestRepository.save(request));
    }

    @Transactional
    public PriorityResultDto prioritize(UUID requestId) {
        PharmacyRequest request = loadWithItems(requestId);
        assertDepot(request.getDepot().getId());
        PriorityResultDto priority = priorityService.calculateFor(request);
        if (request.getStatus() == RequestStatus.SUBMITTED || request.getStatus() == RequestStatus.RECEIVED) {
            request.setStatus(RequestStatus.PRIORITIZED);
            requestRepository.save(request);
        }
        notifications.notifyPharmacy(request.getPharmacy().getId(), NotificationType.PRIORITY_CALCULATED,
                "Priority calculated",
                "Depot computed a priority of " + priority.coefficient() + "/100 for your request.",
                request.getId(), "REQUEST");
        return priority;
    }

    @Transactional
    public RequestDto approveForPlanning(UUID requestId) {
        PharmacyRequest request = loadWithItems(requestId);
        assertDepot(request.getDepot().getId());
        if (request.getStatus() != RequestStatus.PRIORITIZED) {
            throw new BusinessRuleException("Request must be PRIORITIZED before planning approval");
        }
        request.setStatus(RequestStatus.PLANNED);
        RequestDto dto = assembler.toDto(requestRepository.save(request));
        // Hands over to the automated route step (no-op unless stockcare.workflow.auto-route=true).
        events.publishEvent(new com.chanebplus.stockcare.common.events.WorkflowEvents
                .RequestApprovedForPlanning(requestId, request.getDepot().getId()));
        return dto;
    }

    // ---------------- Helpers ----------------

    private PharmacyRequestItem newItem(RequestItemInput in) {
        PharmacyRequestItem item = new PharmacyRequestItem();
        item.setMedication(em.getReference(Medication.class, in.medicationId()));
        item.setRequestedQuantity(in.requestedQuantity());
        item.setNote(in.note());
        return item;
    }

    private Depot resolveDepot(UUID pharmacyId) {
        return depotPharmacyRepository.findFirstByPharmacyId(pharmacyId)
                .map(link -> link.getDepot())
                .orElseThrow(() -> new BusinessRuleException("Pharmacy is not associated with any depot"));
    }

    private PharmacyRequest loadWithItems(UUID requestId) {
        return requestRepository.findWithItemsById(requestId)
                .orElseThrow(() -> NotFoundException.of("PharmacyRequest", requestId));
    }

    private PharmacyRequest loadOwnedByPharmacy(UUID pharmacyId, UUID requestId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        PharmacyRequest request = loadWithItems(requestId);
        if (!request.getPharmacy().getId().equals(pharmacyId)) {
            throw new ForbiddenAccessException("Request does not belong to this pharmacy");
        }
        return request;
    }

    private void assertDepot(UUID depotId) {
        if (SecurityUtils.isAdmin()) {
            return;
        }
        if (SecurityUtils.currentRole() != com.chanebplus.stockcare.modules.user.domain.Role.DEPOT
                || !SecurityUtils.currentDepotId().equals(depotId)) {
            throw new ForbiddenAccessException("Depot access required for this depot");
        }
    }
}
