package com.chanebplus.stockcare.modules.prediction.service;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.prediction.spi.ShortagePrediction;
import com.chanebplus.stockcare.modules.prediction.spi.StockPredictionService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real prediction integration: calls the Python StockCare Prediction Service (LightGBM / NumPy-GBT)
 * over HTTP. Activated with {@code stockcare.models.prediction.mode=external}. Resilient by design —
 * on any transport error it logs and returns an empty list so the workflow degrades gracefully
 * (the depot simply sees no new predictions) rather than failing the request.
 */
@Service
@ConditionalOnProperty(name = "stockcare.models.prediction.mode", havingValue = "external")
public class ExternalStockPredictionService implements StockPredictionService {

    private static final Logger log = LoggerFactory.getLogger(ExternalStockPredictionService.class);

    private final ModelsProperties models;
    private final RestClient client;

    public ExternalStockPredictionService(ModelsProperties models) {
        this.models = models;
        String url = models.getPrediction().getUrl();
        long timeout = models.getPrediction().getTimeoutMs();
        RestClient.Builder builder = RestClient.builder();
        if (url != null && !url.isBlank()) {
            var settings = ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofMillis(Math.min(timeout, 3000)))
                    .withReadTimeout(Duration.ofMillis(timeout));
            builder = builder.baseUrl(url).requestFactory(ClientHttpRequestFactories.get(settings));
        }
        this.client = builder.build();
        log.info("ExternalStockPredictionService wired to {}", url);
    }

    @Override
    public String mode() {
        return "external";
    }

    @Override
    public List<ShortagePrediction> predictShortages(Pharmacy pharmacy, List<InventoryItem> inventory,
                                                     Instant predictionTime) {
        String url = models.getPrediction().getUrl();
        if (url == null || url.isBlank() || inventory.isEmpty()) {
            return List.of();
        }
        List<Item> items = new ArrayList<>();
        for (InventoryItem it : inventory) {
            items.add(new Item(
                    it.getMedication().getId().toString(),
                    pharmacy.getRegion(),
                    it.getMedication().getCategory(),
                    it.getMedication().isColdChain(),
                    it.getCurrentQuantity(),
                    it.getAverageDailyConsumption()));
        }
        PredictRequest request = new PredictRequest(predictionTime.toString(),
                models.getPrediction().getHorizonDays(), items);

        try {
            PredictResponse response = client.post()
                    .uri("/predict")
                    .body(request)
                    .retrieve()
                    .body(PredictResponse.class);
            if (response == null || response.predictions() == null) {
                return List.of();
            }
            List<ShortagePrediction> out = new ArrayList<>();
            for (Prediction p : response.predictions()) {
                out.add(new ShortagePrediction(
                        UUID.fromString(p.medicationId()),
                        p.currentStock(),
                        p.predictedShortageDate() != null ? LocalDate.parse(p.predictedShortageDate()) : null,
                        p.estimatedRemainingDays(),
                        p.predictedMissingQuantity(),
                        p.confidence(),
                        p.reason(),
                        p.modelVersion() != null ? p.modelVersion() : response.modelVersion(),
                        false));
            }
            return out;
        } catch (RuntimeException ex) {
            log.warn("Prediction service call failed ({}); returning no predictions", ex.getMessage());
            return List.of();
        }
    }

    // ---- transport DTOs ----

    record Item(String medicationId, String region, String category, boolean coldChain,
                int currentStock, double averageDailyConsumption) {}

    record PredictRequest(String decisionDate, int horizonDays, List<Item> items) {}

    record Prediction(String medicationId, int currentStock, String predictedShortageDate,
                      Integer estimatedRemainingDays, int predictedMissingQuantity, Double demandForecast,
                      Double confidence, String urgency, String reason, String modelVersion) {}

    record PredictResponse(String modelVersion, String backend, boolean simulated, int horizonDays,
                           String decisionDate, List<Prediction> predictions) {}
}
