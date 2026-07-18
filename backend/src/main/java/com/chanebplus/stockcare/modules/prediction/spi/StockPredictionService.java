package com.chanebplus.stockcare.modules.prediction.spi;

import com.chanebplus.stockcare.modules.inventory.domain.InventoryItem;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import java.time.Instant;
import java.util.List;

/**
 * Replaceable shortage-prediction boundary. The MVP ships a transparent rule-based mock; a future
 * LightGBM demand model (see model.txt / cahier des charges) can be dropped in behind this same
 * interface, wired via {@code stockcare.models.prediction.mode}.
 */
public interface StockPredictionService {

    List<ShortagePrediction> predictShortages(Pharmacy pharmacy,
                                              List<InventoryItem> inventory,
                                              Instant predictionTime);

    /** Implementation mode identifier, e.g. "mock" or "external". */
    String mode();
}
