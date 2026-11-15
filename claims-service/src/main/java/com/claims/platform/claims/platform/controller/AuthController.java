package com.claims.platform.claims.platform.controller;

import com.claims.platform.claims.platform.dto.request.LoginRequest;
import com.claims.platform.claims.platform.dto.response.TokenResponse;
import com.claims.platform.claims.platform.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Username and password login. The token is then required on every claimant, staff and admin call. */
@RestController
@Tag(name = "Authentication", description = "Sign in with a platform account")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Sign in", description = "Returns a bearer token for a customer, handler, team lead or manager.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "A bearer token valid for the other endpoints")
    @ApiResponse(responseCode = "400", description = "Username or password missing (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Unknown username or password (UNAUTHENTICATED)")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }
}
