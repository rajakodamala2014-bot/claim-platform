package com.claims.platform.claims.handling.infrastructure;

import com.claims.platform.claims.core.application.AttachDocuments;
import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimWorkQueries;
import com.claims.platform.claims.core.application.ClaimantClaims;
import com.claims.platform.claims.core.application.StaffClaims;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.handling.application.AssignClaim;
import com.claims.platform.claims.handling.application.CloseCoolingOffClaims;
import com.claims.platform.claims.handling.application.ExpireInformationRequests;
import com.claims.platform.claims.handling.application.AuthorityLimits;
import com.claims.platform.claims.handling.application.MyInformationRequests;
import com.claims.platform.claims.handling.application.ProposeDecision;
import com.claims.platform.claims.handling.application.RecordAssessment;
import com.claims.platform.claims.handling.application.RequestInformation;
import com.claims.platform.claims.handling.application.RespondToInformationRequest;
import com.claims.platform.claims.handling.application.ReviewDecision;
import com.claims.platform.claims.handling.application.SetReserve;
import com.claims.platform.claims.handling.application.StartAssessment;
import com.claims.platform.claims.platform.application.MarketConfigs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the handling module's use cases. */
@Configuration
public class HandlingConfig {

    @Bean
    MyInformationRequests myInformationRequests(ClaimantClaims claims) {
        return new MyInformationRequests(claims);
    }

    @Bean
    RespondToInformationRequest respondToInformationRequest(ClaimantClaims claimantClaims, ClaimRepository claims,
                                                            AttachDocuments attachDocuments, UnitOfWork unitOfWork,
                                                            Clock clock) {
        return new RespondToInformationRequest(claimantClaims, claims, attachDocuments, unitOfWork, clock);
    }

    @Bean
    ExpireInformationRequests expireInformationRequests(ClaimWorkQueries work, ClaimRepository claims,
                                                        UnitOfWork unitOfWork, Clock clock) {
        return new ExpireInformationRequests(work, claims, unitOfWork, clock);
    }

    @Bean
    CloseCoolingOffClaims closeCoolingOffClaims(ClaimWorkQueries work, ClaimRepository claims, MarketConfigs markets,
                                                UnitOfWork unitOfWork, Clock clock) {
        return new CloseCoolingOffClaims(work, claims, markets, unitOfWork, clock);
    }

    @Bean
    AssignClaim assignClaim(StaffClaims staffClaims, Clock clock) {
        return new AssignClaim(staffClaims, clock);
    }

    @Bean
    StartAssessment startAssessment(StaffClaims staffClaims, Clock clock) {
        return new StartAssessment(staffClaims, clock);
    }

    @Bean
    SetReserve setReserve(StaffClaims staffClaims, Clock clock) {
        return new SetReserve(staffClaims, clock);
    }

    @Bean
    RequestInformation requestInformation(StaffClaims staffClaims, MarketConfigs markets, Clock clock) {
        return new RequestInformation(staffClaims, markets, clock);
    }

    @Bean
    RecordAssessment recordAssessment(StaffClaims staffClaims, Clock clock) {
        return new RecordAssessment(staffClaims, clock);
    }

    @Bean
    ProposeDecision proposeDecision(StaffClaims staffClaims, AuthorityLimits authority, Clock clock) {
        return new ProposeDecision(staffClaims, authority, clock);
    }

    @Bean
    ReviewDecision reviewDecision(StaffClaims staffClaims, AuthorityLimits authority, Clock clock) {
        return new ReviewDecision(staffClaims, authority, clock);
    }
}
