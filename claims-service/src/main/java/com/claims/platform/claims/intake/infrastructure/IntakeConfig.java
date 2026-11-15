package com.claims.platform.claims.intake.infrastructure;

import com.claims.platform.claims.core.application.AttachDocuments;
import com.claims.platform.claims.core.application.ClaimQueries;
import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimWorkQueries;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.DocumentRepository;
import com.claims.platform.claims.core.application.DocumentStorage;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.intake.application.AppealMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaims;
import com.claims.platform.claims.intake.application.GetStaffClaim;
import com.claims.platform.claims.intake.application.IntakeRecords;
import com.claims.platform.claims.intake.application.ListMyPolicies;
import com.claims.platform.claims.intake.application.PolicyGateway;
import com.claims.platform.claims.intake.application.RequestUpload;
import com.claims.platform.claims.intake.application.RetryCoverChecks;
import com.claims.platform.claims.intake.application.SubmitClaim;
import com.claims.platform.claims.intake.application.WithdrawMyClaim;
import com.claims.platform.claims.platform.application.IdempotencyStore;
import com.claims.platform.claims.platform.application.MarketConfigs;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Wires the intake module's use cases (plain classes) and adapters. */
@Configuration
@EnableConfigurationProperties(PolicySystemProperties.class)
public class IntakeConfig {

    @Bean
    PolicyGateway policyGateway(RestClient.Builder builder, PolicySystemProperties properties) {
        return new RestPolicyGateway(builder, properties);
    }

    @Bean
    SubmitClaim submitClaim(ClaimRepository claims, IntakeRecords intake, PolicyGateway policies, MarketConfigs markets,
                            AttachDocuments attachDocuments, IdempotencyStore idempotency, UnitOfWork unitOfWork, Clock clock) {
        return new SubmitClaim(claims, intake, policies, markets, attachDocuments, idempotency, unitOfWork, clock);
    }

    @Bean
    GetStaffClaim getStaffClaim(StaffClaims staffClaims, DocumentRepository documents, IntakeRecords intake,
                                ClaimQueries queries) {
        return new GetStaffClaim(staffClaims, documents, intake, queries);
    }

    @Bean
    RetryCoverChecks retryCoverChecks(ClaimWorkQueries work, ClaimRepository claims, IntakeRecords intake,
                                      PolicyGateway policies, MarketConfigs markets, UnitOfWork unitOfWork, Clock clock) {
        return new RetryCoverChecks(work, claims, intake, policies, markets, unitOfWork, clock);
    }

    @Bean
    ListMyPolicies listMyPolicies(PolicyGateway policies) {
        return new ListMyPolicies(policies);
    }

    @Bean
    RequestUpload requestUpload(DocumentRepository documents, DocumentStorage storage, Clock clock) {
        return new RequestUpload(documents, storage, clock);
    }

    @Bean
    GetMyClaims getMyClaims(ClaimQueries queries) {
        return new GetMyClaims(queries);
    }

    @Bean
    GetMyClaim getMyClaim(ClaimantClaims claims, DocumentRepository documents, ClaimQueries queries) {
        return new GetMyClaim(claims, documents, queries);
    }

    @Bean
    WithdrawMyClaim withdrawMyClaim(ClaimantClaims claimantClaims, ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        return new WithdrawMyClaim(claimantClaims, claims, unitOfWork, clock);
    }

    @Bean
    AppealMyClaim appealMyClaim(ClaimantClaims claimantClaims, ClaimRepository claims, UnitOfWork unitOfWork, Clock clock) {
        return new AppealMyClaim(claimantClaims, claims, unitOfWork, clock);
    }
}
