package com.claims.platform.claims.core.config;

import com.claims.platform.claims.core.common.UploadUrlSigner;
import com.claims.platform.claims.core.service.DocumentStorageService;
import com.claims.platform.claims.core.service.impl.LocalDocumentStorageServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Document storage: the filesystem implementation with HMAC-signed upload URLs (an S3 one can replace it). */
@Configuration
@EnableConfigurationProperties(DocumentStorageProperties.class)
public class StorageConfig {

    @Bean
    UploadUrlSigner uploadUrlSigner(DocumentStorageProperties properties) {
        return new UploadUrlSigner(properties.signingSecret());
    }

    @Bean
    DocumentStorageService documentStorageService(DocumentStorageProperties properties, UploadUrlSigner signer) {
        return new LocalDocumentStorageServiceImpl(properties, signer);
    }
}
