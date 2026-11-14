package com.claims.platform.identity.web;

import com.claims.platform.identity.service.AuthService;
import com.claims.platform.identity.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final AuthService authService;
    private final PolicyService policyService;

    public IdentityController(AuthService authService, PolicyService policyService) {
        this.authService = authService;
        this.policyService = policyService;
    }

    @PostMapping("/auth/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        return authService.login(request);
    }

    @GetMapping("/policies")
    public Object policies(@RequestParam(required = false) String policyNumber) {
        if (policyNumber != null && !policyNumber.isBlank()) {
            return policyService.getByNumber(policyNumber);
        }
        return policyService.mine();
    }

    @GetMapping("/users/{userId}")
    public UserResponse user(@PathVariable String userId) {
        return authService.getUser(userId);
    }
}
