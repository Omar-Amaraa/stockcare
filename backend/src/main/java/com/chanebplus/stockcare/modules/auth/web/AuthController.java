package com.chanebplus.stockcare.modules.auth.web;

import com.chanebplus.stockcare.modules.auth.dto.AuthUser;
import com.chanebplus.stockcare.modules.auth.dto.LoginRequest;
import com.chanebplus.stockcare.modules.auth.dto.LoginResponse;
import com.chanebplus.stockcare.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Authenticate and receive a JWT")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Return the currently authenticated user")
    @GetMapping("/me")
    public AuthUser me() {
        return authService.currentUser();
    }
}
