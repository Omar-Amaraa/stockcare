package com.chanebplus.stockcare.modules.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chanebplus.stockcare.config.ModelsProperties;
import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.medication.domain.Medication;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.prediction.service.MockStockPredictionService;
import com.chanebplus.stockcare.modules.prediction.spi.ShortagePrediction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockStockPredictionServiceTest {

    private final MockStockPredictionService service = new MockStockPredictionService(new ModelsProperties());

    private InventoryItem item(int current, int min, double adc) {
        Medication m = new Medication();
        m.setName("Test med");
        InventoryItem i = new InventoryItem();
        i.setMedication(m);
        i.setCurrentQuantity(current);
        i.setMinimumQuantity(min);
        i.setAverageDailyConsumption(adc);
        return i;
    }

    @Test
    void predictsShortageWhenGapPositive() {
        List<ShortagePrediction> out = service.predictShortages(new Pharmacy(),
                List.of(item(12, 20, 4.0)), Instant.parse("2026-01-15T09:00:00Z"));
        assertThat(out).hasSize(1);
        ShortagePrediction p = out.get(0);
        assertThat(p.simulated()).isTrue();
        assertThat(p.predictedMissingQuantity()).isPositive();
        assertThat(p.reason()).contains("SIMULATED");
    }

    @Test
    void noShortageWhenStockAmpleAndNoConsumption() {
        assertThat(service.predictShortages(new Pharmacy(), List.of(item(500, 20, 0.0)), Instant.now())).isEmpty();
    }
}
