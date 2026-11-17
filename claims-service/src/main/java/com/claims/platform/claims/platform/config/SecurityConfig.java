package com.claims.platform.claims.platform.config;

import com.claims.platform.claims.core.service.DocumentStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
 * Claims Service is an OAuth2 resource server. Surfaces are separated by role:
 * {@code /claimant/v1/**} customers only, {@code /staff/v1/**} staff only; anything unlisted is denied.
 * Ownership (claimants) and market scoping (staff) are enforced in the application layer.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    static final String[] STAFF = {"CLAIMS_ADJUSTER"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                            AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // pre-signed upload URL: authorised by its HMAC signature, like S3
                        .requestMatchers(HttpMethod.PUT, DocumentStorageService.UPLOAD_CONTENT_PATH.replace("{uploadId}", "*")).permitAll()
                        // Payments fallback webhook: authorised by its HMAC signature; not routed by the gateway
                        .requestMatchers(HttpMethod.POST, "/internal/v1/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/claimant/**").hasRole("CUSTOMER")
                        .requestMatchers("/staff/**").hasAnyRole(STAFF)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
