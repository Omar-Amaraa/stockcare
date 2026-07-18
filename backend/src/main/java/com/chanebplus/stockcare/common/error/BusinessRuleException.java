package com.chanebplus.stockcare.common.error;

/** Thrown when a domain invariant or workflow rule is violated. Maps to HTTP 409/400. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
