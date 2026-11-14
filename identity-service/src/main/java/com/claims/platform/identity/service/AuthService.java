package com.claims.platform.identity.service;

import com.claims.platform.common.security.AuthenticatedUser;
import com.claims.platform.common.security.JwtService;
import com.claims.platform.identity.domain.AppUser;
import com.claims.platform.identity.domain.UserMarket;
import com.claims.platform.identity.repo.AppUserRepository;
import com.claims.platform.identity.repo.TeamMemberRepository;
import com.claims.platform.identity.repo.UserCredentialRepository;
import com.claims.platform.identity.repo.UserMarketRepository;
import com.claims.platform.identity.web.TokenRequest;
import com.claims.platform.identity.web.TokenResponse;
import com.claims.platform.identity.web.UserResponse;
import com.claims.platform.web.ApiException;
import com.claims.platform.web.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final UserCredentialRepository credentials;
    private final UserMarketRepository markets;
    private final TeamMemberRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, UserCredentialRepository credentials, UserMarketRepository markets,
                       TeamMemberRepository teams, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.credentials = credentials;
        this.markets = markets;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(TokenRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.username())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));
        var credential = credentials.findById(user.getId())
                .orElseThrow(() -> ApiException.unauthorized("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid credentials");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw ApiException.forbidden("User is not active");
        }
        AuthenticatedUser principal = principal(user);
        JwtService.IssuedToken issued = jwtService.issue(principal);
        return new TokenResponse(issued.accessToken(), issued.expiresIn(),
                new TokenResponse.User(user.getId(), user.getRole(), principal.market()));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(String userId) {
        CurrentUser.get();
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        AuthenticatedUser principal = principal(user);
        return new UserResponse(user.getId(), user.getFullName(), user.getRole(), principal.market(),
                principal.markets(), principal.teamId(), user.getStatus());
    }

    private AuthenticatedUser principal(AppUser user) {
        List<String> marketCodes = markets.findByUserId(user.getId()).stream()
                .map(market -> market.getMarketCode().trim())
                .toList();
        String primary = marketCodes.isEmpty() ? null : marketCodes.getFirst();
        String teamId = teams.findFirstByUserId(user.getId()).map(member -> member.getTeamId()).orElse(null);
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                primary, marketCodes, teamId);
    }
}
