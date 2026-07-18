package com.chanebplus.stockcare.integration.erp;

import java.time.LocalDate;

/** Inventory line as returned by a pharmacy ERP. Decoupled from our domain model. */
public record ExternalInventoryItem(
        String externalSku,
        String medicationName,
        int quantity,
        LocalDate expirationDate) {}
