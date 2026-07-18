package com.chanebplus.stockcare.modules.priority.service;

import com.chanebplus.stockcare.common.error.NotFoundException;
import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.integration.model.ModelExecutionRecorder;
import com.chanebplus.stockcare.integration.model.ModelExecutionStatus;
import com.chanebplus.stockcare.integration.model.ModelType;
import com.chanebplus.stockcare.modules.priority.domain.PriorityResult;
import com.chanebplus.stockcare.modules.priority.repo.PriorityResultRepository;
import com.chanebplus.stockcare.modules.priority.spi.PriorityCalculationService;
import com.chanebplus.stockcare.modules.priority.spi.PriorityOutcome;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.dto.PriorityResultDto;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates priority scoring for a request and persists the transparent result. */
@Service
public class PriorityService {

    private final PriorityCalculationService calculator;
    private final PriorityResultRepository repository;
    private final PriorityMapper mapper;
    private final ModelExecutionRecorder recorder;
    private final ModelsProperties models;
    private final ApplicationClock clock;

    public PriorityService(PriorityCalculationService calculator, PriorityResultRepository repository,
                           PriorityMapper mapper, ModelExecutionRecorder recorder,
                           ModelsProperties models, ApplicationClock clock) {
        this.calculator = calculator;
        this.repository = repository;
        this.mapper = mapper;
        this.recorder = recorder;
        this.models = models;
        this.clock = clock;
    }

    @Transactional
    public PriorityResultDto calculateFor(PharmacyRequest request) {
        long start = System.currentTimeMillis();
        PriorityOutcome outcome = calculator.calculatePriority(request);

        PriorityResult result = repository.findByRequestId(request.getId()).orElseGet(PriorityResult::new);
        result.setRequest(request);
        result.setCoefficient(outcome.coefficient());
        result.setFactorsJson(mapper.toJson(outcome.factors()));
        result.setExplanation(outcome.explanation());
        result.setCalculationVersion(outcome.version());
        result.setSimulated(outcome.simulated());
        result.setCalculatedAt(clock.now());
        result = repository.save(result);

        recorder.record(ModelType.PRIORITY, calculator.mode(), models.getPriority().getVersion(),
                ModelExecutionStatus.SUCCESS, System.currentTimeMillis() - start,
                "request=" + request.getId() + ", urgency=" + request.getUrgency(),
                "coefficient=" + outcome.coefficient(), null);

        return mapper.toDto(result);
    }

    @Transactional(readOnly = true)
    public PriorityResultDto getByRequest(UUID requestId) {
        return mapper.toDto(repository.findByRequestId(requestId)
                .orElseThrow(() -> NotFoundException.of("PriorityResult for request", requestId)));
    }

    @Transactional(readOnly = true)
    public PriorityResultDto findByRequestOrNull(UUID requestId) {
        return repository.findByRequestId(requestId).map(mapper::toDto).orElse(null);
    }
}
