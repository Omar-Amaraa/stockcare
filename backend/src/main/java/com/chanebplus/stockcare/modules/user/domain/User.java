package com.chanebplus.stockcare.modules.user.domain;

import com.chanebplus.stockcare.common.audit.BaseEntity;
import com.chanebplus.stockcare.modules.depot.domain.Depot;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Application user. A PHARMACY user is bound to exactly one pharmacy; a DEPOT user to exactly one
 * depot. ADMIN users are unbound and used for development/account management.
 */
@Getter
@Setter
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash. Never serialised to clients. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_id")
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id")
    private Depot depot;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
