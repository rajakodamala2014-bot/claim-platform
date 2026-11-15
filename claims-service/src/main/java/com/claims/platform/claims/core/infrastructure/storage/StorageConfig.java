package com.claims.platform.claims.core.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class StorageConfig {

    @Bean
    UploadUrlSigner uploadUrlSigner(DocumentStorageProperties properties) {
        return new UploadUrlSigner(properties.signingSecret());
    }

    @Bean
    LocalDocumentStorage localDocumentStorage(DocumentStorageProperties properties, UploadUrlSigner signer) {
        return new LocalDocumentStorage(properties, signer);
    }
}
