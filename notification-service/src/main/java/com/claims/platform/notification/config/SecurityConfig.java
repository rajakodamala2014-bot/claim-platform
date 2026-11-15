package com.claims.platform.notification.config;

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
 * Customers: their own inbox and preferences. Staff: delivery log of a claim. Managers: templates.
 * Provider callbacks are authenticated by an HMAC signature instead of a user token.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {

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
                        .requestMatchers(HttpMethod.POST, "/internal/v1/providers/*/delivery-status").permitAll()
                        .requestMatchers("/claimant/**").hasRole("CLAIMANT")
                        .requestMatchers("/staff/**").hasAnyRole("HANDLER", "TEAM_LEAD", "MANAGER")
                        .requestMatchers("/admin/**").hasRole("MANAGER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .build();
    }
}
