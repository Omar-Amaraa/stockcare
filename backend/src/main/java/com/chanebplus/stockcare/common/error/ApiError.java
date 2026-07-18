package com.chanebplus.stockcare.common.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Consistent error payload returned by the API. Never leaks stack traces or secrets. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }
}
