package com.chanebplus.stockcare.integration.erp;

import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import java.util.List;

/**
 * Prepared boundary for future ERP connectivity. Domain services must not depend on a specific ERP;
 * they depend on this interface. The MVP ships a manual/mock adapter.
 */
public interface PharmacyErpAdapter {

    List<ExternalInventoryItem> fetchInventory(Pharmacy pharmacy);

    void acknowledgeRequest(PharmacyRequest request);

    String mode();
}
