package com.chanebplus.stockcare.security;

import com.chanebplus.stockcare.common.error.ForbiddenAccessException;
import com.chanebplus.stockcare.modules.user.domain.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helpers to access the authenticated principal and enforce ownership rules from services. */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static CustomUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new ForbiddenAccessException("No authenticated user in context");
        }
        return details;
    }

    public static UUID currentUserId() {
        return currentUser().getUserId();
    }

    public static Role currentRole() {
        return currentUser().getRole();
    }

    public static UUID currentPharmacyId() {
        UUID id = currentUser().getPharmacyId();
        if (id == null) {
            throw new ForbiddenAccessException("Current user is not bound to a pharmacy");
        }
        return id;
    }

    public static UUID currentDepotId() {
        UUID id = currentUser().getDepotId();
        if (id == null) {
            throw new ForbiddenAccessException("Current user is not bound to a depot");
        }
        return id;
    }

    public static boolean isAdmin() {
        return currentUser().getRole() == Role.ADMIN;
    }
}
