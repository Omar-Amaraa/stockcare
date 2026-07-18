package com.chanebplus.stockcare.integration.erp;

import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manual/mock ERP adapter for the MVP. Inventory is managed by hand in the app, so fetchInventory
 * returns nothing and acknowledgeRequest is a no-op log. A real ERP adapter replaces this bean.
 */
@Component
public class ManualPharmacyErpAdapter implements PharmacyErpAdapter {

    private static final Logger log = LoggerFactory.getLogger(ManualPharmacyErpAdapter.class);

    @Override
    public List<ExternalInventoryItem> fetchInventory(Pharmacy pharmacy) {
        return List.of();
    }

    @Override
    public void acknowledgeRequest(PharmacyRequest request) {
        log.debug("[manual-erp] acknowledge request {}", request.getId());
    }

    @Override
    public String mode() {
        return "manual";
    }
}
