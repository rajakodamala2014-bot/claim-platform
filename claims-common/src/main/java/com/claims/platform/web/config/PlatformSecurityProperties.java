package com.claims.platform.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How services validate access tokens.
 * <ul>
 *     <li>{@code issuer-uri} set (real environments): validate against the identity provider's OIDC metadata/JWKS.</li>
 *     <li>otherwise (local/tests): HS256 tokens from the dev token issuer, signed with {@code dev-secret}.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "platform.security")
public record PlatformSecurityProperties(String issuerUri, String devSecret, String devIssuer) {
    public static final String DEFAULT_DEV_ISSUER = "claims-dev-idp";

    public PlatformSecurityProperties {
        devIssuer = devIssuer == null || devIssuer.isBlank() ? DEFAULT_DEV_ISSUER : devIssuer;
    }

    public boolean usesIdentityProvider() {
        return issuerUri != null && !issuerUri.isBlank();
    }
}
