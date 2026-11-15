package com.claims.platform.claims.platform.config;

import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
import com.claims.platform.web.config.PlatformSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Sign-in wiring. Password checking and token claims stay in {@code AuthService}. */
@Configuration
public class AuthConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /** Signs access tokens with the same HS256 key the other services use to validate them. */
    @Bean
    JwtEncoder jwtEncoder(PlatformSecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(PlatformSecurityAutoConfiguration.devKey(properties.devSecret())));
    }
}
