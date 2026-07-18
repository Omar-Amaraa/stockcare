package com.chanebplus.stockcare.modules.auth.dto;

import com.chanebplus.stockcare.modules.user.domain.Role;
import java.util.UUID;

/** Safe view of the authenticated user. Never contains the password hash. */
public record AuthUser(
        UUID id,
        String email,
        String fullName,
        Role role,
        UUID pharmacyId,
        String pharmacyName,
        UUID depotId,
        String depotName) {}
