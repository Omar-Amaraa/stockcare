package com.chanebplus.stockcare.modules.auth.dto;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        AuthUser user) {}
