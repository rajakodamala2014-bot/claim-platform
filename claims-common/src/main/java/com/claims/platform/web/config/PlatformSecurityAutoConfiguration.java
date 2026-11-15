package com.claims.platform.web.config;

import com.claims.platform.web.exception.Problems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Shared OAuth2 resource-server plumbing. Each service still declares its own SecurityFilterChain (the paths
 * differ) and plugs these beans into it.
 */
@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(PlatformSecurityProperties.class)
public class PlatformSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(PlatformSecurityProperties properties) {
        if (properties.usesIdentityProvider()) {
            return JwtDecoders.fromIssuerLocation(properties.issuerUri());
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(devKey(properties.devSecret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.devIssuer()));
        return decoder;
    }

    /** {@code roles} claim -> {@code ROLE_<role>} authorities; principal name = {@code sub}. */
    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            return authorities;
        });
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    AuthenticationEntryPoint problemAuthenticationEntryPoint(ObjectMapper json) {
        return (request, response, ex) -> write(json, response,
                Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid bearer token is required"));
    }

    @Bean
    @ConditionalOnMissingBean
    AccessDeniedHandler problemAccessDeniedHandler(ObjectMapper json) {
        return (request, response, ex) -> write(json, response,
                Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action"));
    }

    /** Key for the local HS256 issuer; shared by the issuer (claims-service dev profile) and all services. */
    public static SecretKeySpec devKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "platform.security.dev-secret must be at least 32 bytes when no issuer-uri is configured");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static void write(ObjectMapper json, HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(Problems.PROBLEM_JSON.toString());
        json.writeValue(response.getOutputStream(), problem);
    }
}
