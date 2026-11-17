package com.claims.platform.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Central token validation: an API request without a valid token, or with the wrong role for the surface,
 * is rejected here and never reaches a service. The decoder, role mapping and problem+json responses are the
 * shared ones from claims-web, so the gateway and the services accept exactly the same tokens.
 * <p>
 * This is the coarse check (surface by role). Ownership and market scoping stay in the services, which also
 * re-validate the token (the {@code Authorization} header is forwarded unchanged).
 */
@Configuration
class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                            AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // pre-signed upload URL: authorised by its HMAC signature (checked by claims-service)
                        .requestMatchers(HttpMethod.PUT, "/claimant/v1/uploads/*/content").permitAll()
                        .requestMatchers("/claimant/**").hasRole("CUSTOMER")
                        .requestMatchers("/staff/**").hasRole("CLAIMS_ADJUSTER")
                        .requestMatchers("/admin/**").hasRole("CLAIMS_ADJUSTER")
                        // the rest carries no user token: the portal's static files, POST /auth/login,
                        // provider callbacks (HMAC, checked by notification-service) and paths the routing
                        // table answers with 404
                        .anyRequest().permitAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
