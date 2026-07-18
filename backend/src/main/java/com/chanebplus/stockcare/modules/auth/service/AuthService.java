package com.chanebplus.stockcare.modules.auth.service;

import com.chanebplus.stockcare.modules.auth.dto.AuthUser;
import com.chanebplus.stockcare.modules.auth.dto.LoginRequest;
import com.chanebplus.stockcare.modules.auth.dto.LoginResponse;
import com.chanebplus.stockcare.modules.user.domain.User;
import com.chanebplus.stockcare.modules.user.repo.UserRepository;
import com.chanebplus.stockcare.security.CustomUserDetails;
import com.chanebplus.stockcare.security.JwtService;
import java.time.Instant;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.setLastLoginAt(Instant.now());

        String token = jwtService.generateToken(principal);
        return new LoginResponse(token, "Bearer", jwtService.expiresAt(), toAuthUser(user));
    }

    @Transactional(readOnly = true)
    public AuthUser currentUser() {
        var principal = com.chanebplus.stockcare.security.SecurityUtils.currentUser();
        User user = userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toAuthUser(user);
    }

    public static AuthUser toAuthUser(User user) {
        return new AuthUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getPharmacy() != null ? user.getPharmacy().getId() : null,
                user.getPharmacy() != null ? user.getPharmacy().getName() : null,
                user.getDepot() != null ? user.getDepot().getId() : null,
                user.getDepot() != null ? user.getDepot().getName() : null);
    }
}
