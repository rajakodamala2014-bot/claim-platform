package com.claims.platform.claims.core.infrastructure;

import com.claims.platform.claims.core.application.AttachDocuments;
import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.CompleteUpload;
import com.claims.platform.claims.core.application.DocumentRepository;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the framework-free core application services. */
@Configuration
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ClaimantClaims claimantClaims(ClaimRepository claims) {
        return new ClaimantClaims(claims);
    }

    @Bean
    StaffClaims staffClaims(ClaimRepository claims, UnitOfWork unitOfWork) {
        return new StaffClaims(claims, unitOfWork);
    }

    @Bean
    AttachDocuments attachDocuments(DocumentRepository documents) {
        return new AttachDocuments(documents);
    }

    @Bean
    CompleteUpload completeUpload(DocumentRepository documents, Clock clock) {
        return new CompleteUpload(documents, clock);
    }
}
