package com.chanebplus.stockcare.modules.prediction.service;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.prediction.spi.ShortagePrediction;
import com.chanebplus.stockcare.modules.prediction.spi.StockPredictionService;
import com.chanebplus.stockcare.time.api.ApplicationClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rule-based, clearly-labelled shortage predictor. Implements the shortage-gap logic from the
 * cahier des charges: G = max(0, D + S - X) where D is forecast demand over the horizon,
 * S the safety stock (minimum desired) and X the current stock. NOT a trained model.
 */
@Service
@ConditionalOnProperty(name = "stockcare.models.prediction.mode", havingValue = "mock", matchIfMissing = true)
public class MockStockPredictionService implements StockPredictionService {

    private final ModelsProperties models;

    public MockStockPredictionService(ModelsProperties models) {
        this.models = models;
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public List<ShortagePrediction> predictShortages(Pharmacy pharmacy, List<InventoryItem> inventory,
                                                     Instant predictionTime) {
        int horizon = models.getPrediction().getHorizonDays();
        String version = models.getPrediction().getVersion();
        LocalDate today = LocalDate.ofInstant(predictionTime, ApplicationClock.ZONE);
        List<ShortagePrediction> results = new ArrayList<>();

        for (InventoryItem item : inventory) {
            double daily = item.getAverageDailyConsumption();
            if (daily <= 0) {
                continue; // cannot forecast without a consumption rate
            }
            int current = item.getCurrentQuantity();
            int safety = item.getMinimumQuantity();

            double forecastDemand = daily * horizon;
            int gap = (int) Math.ceil(Math.max(0, forecastDemand + safety - current));
            if (gap <= 0) {
                continue; // no shortage expected within the horizon
            }

            double daysUntilSafety = Math.max(0, (current - safety) / daily);
            int remainingDays = (int) Math.floor(current / daily);
            LocalDate shortageDate = today.plusDays((long) Math.floor(daysUntilSafety));

            String reason = String.format(
                    "[SIMULATED rule-based] Avg daily use %.1f over %d-day horizon forecasts demand %.0f; "
                            + "with safety stock %d and current stock %d, shortfall of %d units expected around %s.",
                    daily, horizon, forecastDemand, safety, current, gap, shortageDate);

            results.add(new ShortagePrediction(
                    item.getMedication().getId(), current, shortageDate, remainingDays, gap,
                    null, reason, version, true));
        }
        return results;
    }
}
