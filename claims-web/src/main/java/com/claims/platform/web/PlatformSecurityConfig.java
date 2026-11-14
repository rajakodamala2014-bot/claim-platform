package com.claims.platform.web;

import com.claims.platform.common.error.ErrorBody;
import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.common.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({ClaimsSecurityProperties.class, EventsProperties.class})
public class PlatformSecurityConfig {
    private static final List<String> DEFAULT_PUBLIC = List.of(
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    @Bean
    public JwtService jwtService(ClaimsSecurityProperties properties) {
        return new JwtService(properties.getJwtSecret(), properties.getIssuer(), properties.getAccessTokenTtl());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   ClaimsSecurityProperties properties, ObjectMapper objectMapper)
            throws Exception {
        List<String> publicPaths = new ArrayList<>(DEFAULT_PUBLIC);
        if (properties.getPublicPaths() != null) {
            publicPaths.addAll(properties.getPublicPaths());
        }
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                write(response, request, 401, ErrorCodes.UNAUTHORIZED, "Missing or invalid token", objectMapper))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                write(response, request, 403, ErrorCodes.NOT_ALLOWED, "You are not allowed to perform this action", objectMapper)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, properties, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status, String code,
                       String message, ObjectMapper objectMapper) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object correlation = request.getAttribute(CorrelationIdFilter.ATTR);
        String correlationId = correlation == null ? null : correlation.toString();
        objectMapper.writeValue(response.getOutputStream(), new ErrorBody(code, message, List.of(), correlationId));
    }
}
