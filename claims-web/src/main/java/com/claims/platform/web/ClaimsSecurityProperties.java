package com.claims.platform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "claims.security")
public class ClaimsSecurityProperties {
    private String jwtSecret;
    private String issuer = "claims-platform";
    private Duration accessTokenTtl = Duration.ofHours(1);
    private String internalToken = "dev-internal-token";
    private List<String> publicPaths = new ArrayList<>();
    private List<String> internalPaths = new ArrayList<>();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getInternalPaths() {
        return internalPaths;
    }

    public void setInternalPaths(List<String> internalPaths) {
        this.internalPaths = internalPaths;
    }
}
