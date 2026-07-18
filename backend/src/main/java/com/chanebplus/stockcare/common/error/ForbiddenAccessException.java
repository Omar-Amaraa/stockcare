package com.chanebplus.stockcare.common.error;

/** Thrown when a user attempts to access data they do not own. Maps to HTTP 403. */
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) {
        super(message);
    }
}
