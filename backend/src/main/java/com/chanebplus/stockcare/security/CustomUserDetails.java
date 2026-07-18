package com.chanebplus.stockcare.security;

import com.chanebplus.stockcare.modules.user.domain.Role;
import com.chanebplus.stockcare.modules.user.domain.User;
import com.chanebplus.stockcare.modules.user.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Security principal carrying ownership context (userId, role, pharmacyId, depotId). */
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final UserStatus status;
    private final UUID pharmacyId;
    private final UUID depotId;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.status = user.getStatus();
        this.pharmacyId = user.getPharmacy() != null ? user.getPharmacy().getId() : null;
        this.depotId = user.getDepot() != null ? user.getDepot().getId() : null;
    }

    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public UUID getPharmacyId() { return pharmacyId; }
    public UUID getDepotId() { return depotId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return status == UserStatus.ACTIVE; }
}
