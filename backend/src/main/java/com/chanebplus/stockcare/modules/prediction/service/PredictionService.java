package com.chanebplus.stockcare.modules.prediction.service;

import com.chanebplus.stockcare.common.events.WorkflowEvents;
import com.chanebplus.stockcare.common.stream.WorkflowBroadcaster;
import com.chanebplus.stockcare.common.stream.WorkflowUpdate;
import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.integration.model.ModelExecutionRecorder;
import com.chanebplus.stockcare.integration.model.ModelExecutionStatus;
import com.chanebplus.stockcare.integration.model.ModelType;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.inventory.repo.InventoryItemRepository;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.medication.service.MedicationMapper;
import com.chanebplus.stockcare.modules.notification.domain.NotificationType;
import com.chanebplus.stockcare.modules.notification.service.NotificationService;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.pharmacy.service.PharmacyAccessService;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionResult;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionRun;
import com.chanebplus.stockcare.modules.prediction.domain.PredictionRunStatus;
import com.chanebplus.stockcare.modules.prediction.dto.PredictionResultDto;
import com.chanebplus.stockcare.modules.prediction.dto.PredictionStateDto;
import com.chanebplus.stockcare.modules.prediction.repo.PredictionResultRepository;
import com.chanebplus.stockcare.modules.prediction.repo.PredictionRunRepository;
import com.chanebplus.stockcare.modules.prediction.spi.ShortagePrediction;
import com.chanebplus.stockcare.modules.prediction.spi.StockPredictionService;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates automated shortage prediction. Runs are triggered by domain events (inventory / time
 * changes), execute the configured {@link StockPredictionService}, and persist both the results and
 * a {@link PredictionRun} state (idempotent via an input hash). Progress is broadcast over SSE so the
 * pharmacy UI updates without any manual "run prediction" action.
 */
@Service
public class PredictionService {

    private final StockPredictionService predictor;
    private final PredictionResultRepository resultRepository;
    private final PredictionRunRepository runRepository;
    private final InventoryItemRepository inventoryRepository;
    private final PharmacyAccessService accessService;
    private final ApplicationClock clock;
    private final ModelExecutionRecorder recorder;
    private final ModelsProperties models;
    private final EntityManager em;
    private final NotificationService notifications;
    private final WorkflowBroadcaster broadcaster;
    private final ApplicationEventPublisher events;

    public PredictionService(StockPredictionService predictor, PredictionResultRepository resultRepository,
                             PredictionRunRepository runRepository, InventoryItemRepository inventoryRepository,
                             PharmacyAccessService accessService, ApplicationClock clock,
                             ModelExecutionRecorder recorder, ModelsProperties models, EntityManager em,
                             NotificationService notifications, WorkflowBroadcaster broadcaster,
                             ApplicationEventPublisher events) {
        this.predictor = predictor;
        this.resultRepository = resultRepository;
        this.runRepository = runRepository;
        this.inventoryRepository = inventoryRepository;
        this.accessService = accessService;
        this.clock = clock;
        this.recorder = recorder;
        this.models = models;
        this.em = em;
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.events = events;
    }

    /**
     * Marks a pharmacy's prediction run as PROCESSING in its own transaction so the state is visible
     * to pollers before the model runs. Returns the correlation id, or {@code null} if the run is
     * skipped because the inventory is unchanged (idempotent) — unless {@code force}.
     */
    @Transactional
    public String beginRun(UUID pharmacyId, boolean force) {
        List<InventoryItem> inventory = inventoryRepository.findByPharmacyId(pharmacyId);
        String hash = hash(inventory);
        PredictionRun run = runRepository.findByPharmacyId(pharmacyId).orElseGet(() -> {
            PredictionRun r = new PredictionRun();
            r.setPharmacyId(pharmacyId);
            return r;
        });
        if (!force && run.getStatus() == PredictionRunStatus.COMPLETED && hash.equals(run.getInputHash())) {
            return null; // unchanged inventory — no duplicate prediction
        }
        String correlationId = UUID.randomUUID().toString();
        run.setStatus(PredictionRunStatus.PROCESSING);
        run.setCorrelationId(correlationId);
        run.setModelVersion(models.getPrediction().getVersion());
        run.setInputHash(hash);
        run.setStartedAt(clock.now());
        run.setErrorMessage(null);
        runRepository.save(run);
        broadcast(pharmacyId, "PROCESSING", "Updating shortage predictions…");
        return correlationId;
    }

    /** Executes the model, stores results and marks the run COMPLETED/FAILED (own transaction). */
    @Transactional
    public void executeRun(UUID pharmacyId, String correlationId) {
        PredictionRun run = runRepository.findByPharmacyId(pharmacyId).orElse(null);
        if (run == null || !correlationId.equals(run.getCorrelationId())) {
            return; // superseded by a newer run
        }
        List<InventoryItem> inventory = inventoryRepository.findByPharmacyId(pharmacyId);
        long start = System.currentTimeMillis();
        try {
            Pharmacy pharmacy = em.getReference(Pharmacy.class, pharmacyId);
            Instant predictionTime = clock.now();
            List<ShortagePrediction> predictions = predictor.predictShortages(pharmacy, inventory, predictionTime);

            resultRepository.deleteByPharmacyId(pharmacyId);
            resultRepository.flush();
            for (ShortagePrediction p : predictions) {
                PredictionResult e = new PredictionResult();
                e.setPharmacy(pharmacy);
                e.setMedication(em.getReference(Medication.class, p.medicationId()));
                e.setCurrentStock(p.currentStock());
                e.setPredictedShortageDate(p.predictedShortageDate());
                e.setEstimatedRemainingDays(p.estimatedRemainingDays());
                e.setPredictedMissingQuantity(p.predictedMissingQuantity());
                e.setConfidence(p.confidence());
                e.setReason(p.reason());
                e.setModelVersion(p.modelVersion());
                e.setSimulated(p.simulated());
                e.setPredictionTime(predictionTime);
                resultRepository.save(e);
            }

            run.setStatus(PredictionRunStatus.COMPLETED);
            run.setCompletedAt(clock.now());
            run.setShortageCount(predictions.size());
            runRepository.save(run);

            recorder.record(ModelType.PREDICTION, predictor.mode(), models.getPrediction().getVersion(),
                    ModelExecutionStatus.SUCCESS, System.currentTimeMillis() - start,
                    "pharmacy=" + pharmacyId + ", items=" + inventory.size(),
                    "predictions=" + predictions.size(), null);

            broadcast(pharmacyId, "COMPLETED",
                    predictions.isEmpty() ? "No shortage predicted" : predictions.size() + " shortage(s) predicted");

            if (!predictions.isEmpty()) {
                notifications.notifyPharmacy(pharmacyId, NotificationType.SHORTAGE_PREDICTED,
                        "Predicted shortages",
                        predictions.size() + " medication(s) may run short soon.", pharmacyId, "PHARMACY");
                events.publishEvent(new WorkflowEvents.ShortagePredicted(pharmacyId));
            }
        } catch (RuntimeException ex) {
            run.setStatus(PredictionRunStatus.FAILED);
            run.setRetryCount(run.getRetryCount() + 1);
            run.setErrorMessage(ex.getMessage());
            runRepository.save(run);
            recorder.record(ModelType.PREDICTION, predictor.mode(), models.getPrediction().getVersion(),
                    ModelExecutionStatus.ERROR, System.currentTimeMillis() - start,
                    "pharmacy=" + pharmacyId, null, ex.getMessage());
            broadcast(pharmacyId, "FAILED", "Prediction failed");
        }
    }

    /** Fallback manual retry (error recovery only) — access-checked, forces a fresh run. */
    @Transactional
    public PredictionStateDto retry(UUID pharmacyId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        String cid = beginRun(pharmacyId, true);
        if (cid != null) {
            executeRun(pharmacyId, cid);
        }
        return state(pharmacyId);
    }

    @Transactional(readOnly = true)
    public PredictionStateDto state(UUID pharmacyId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        PredictionRun run = runRepository.findByPharmacyId(pharmacyId).orElse(null);
        List<PredictionResultDto> predictions = resultRepository
                .findByPharmacyIdOrderByPredictedShortageDateAsc(pharmacyId)
                .stream().map(PredictionService::toDto).toList();

        if (run == null) {
            // No run recorded yet (e.g. seeded data): treat existing results as complete.
            PredictionRunStatus s = predictions.isEmpty() ? PredictionRunStatus.PENDING : PredictionRunStatus.COMPLETED;
            return new PredictionStateDto(s, false, null, models.getPrediction().getVersion(), 0,
                    predictions.size(), null, null, null, predictions);
        }
        boolean outdated = run.getStatus() == PredictionRunStatus.COMPLETED
                && !hash(inventoryRepository.findByPharmacyId(pharmacyId)).equals(run.getInputHash());
        return new PredictionStateDto(run.getStatus(), outdated, run.getCorrelationId(), run.getModelVersion(),
                run.getRetryCount(), run.getShortageCount(), run.getStartedAt(), run.getCompletedAt(),
                run.getErrorMessage(), predictions);
    }

    @Transactional(readOnly = true)
    public List<PredictionResultDto> latest(UUID pharmacyId) {
        accessService.assertCanAccessPharmacy(pharmacyId);
        return resultRepository.findByPharmacyIdOrderByPredictedShortageDateAsc(pharmacyId)
                .stream().map(PredictionService::toDto).toList();
    }

    private void broadcast(UUID pharmacyId, String status, String message) {
        broadcaster.publish(pharmacyId,
                WorkflowUpdate.of("PREDICTION_STATE", "PHARMACY", pharmacyId, status, message));
    }

    private String hash(List<InventoryItem> inventory) {
        StringBuilder sb = new StringBuilder();
        inventory.stream()
                .sorted(java.util.Comparator.comparing(i -> i.getMedication().getId()))
                .forEach(i -> sb.append(i.getMedication().getId()).append(':')
                        .append(i.getCurrentQuantity()).append(':')
                        .append(i.getMinimumQuantity()).append(':')
                        .append(i.getAverageDailyConsumption()).append(':')
                        .append(i.getReorderThreshold()).append('|'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    public static PredictionResultDto toDto(PredictionResult r) {
        return new PredictionResultDto(r.getId(), r.getPharmacy().getId(),
                MedicationMapper.toDto(r.getMedication()), r.getCurrentStock(),
                r.getPredictedShortageDate(), r.getEstimatedRemainingDays(),
                r.getPredictedMissingQuantity(), r.getConfidence(), r.getReason(),
                r.getModelVersion(), r.isSimulated(), r.getPredictionTime());
    }
}
