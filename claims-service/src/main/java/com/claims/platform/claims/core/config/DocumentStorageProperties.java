package com.claims.platform.claims.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * @param storageDir    where the local storage keeps the bytes (a volume in docker-compose)
 * @param publicBaseUrl base URL the app uses to reach this service (the gateway in docker-compose)
 * @param signingSecret HMAC key for pre-signed URLs
 * @param urlTtl        how long a pre-signed URL stays valid
 */
@ConfigurationProperties(prefix = "claims.documents")
public record DocumentStorageProperties(Path storageDir, String publicBaseUrl, String signingSecret, Duration urlTtl) {
    public DocumentStorageProperties {
        storageDir = storageDir == null ? Path.of("data", "documents") : storageDir;
        publicBaseUrl = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl.replaceAll("/+$", "");
        urlTtl = urlTtl == null ? Duration.ofMinutes(15) : urlTtl;
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalStateException("claims.documents.signing-secret must be at least 32 characters");
        }
    }
}
