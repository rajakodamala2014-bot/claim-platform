package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.repository.ClaimWorkRepository;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.intake.repository.IntakeRepository;
import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.entity.CoverCheck;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.claims.intake.service.CoverCheckRetryService;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CoverCheckRetryServiceImpl implements CoverCheckRetryService {
    private static final int BATCH = 100;

    private final ClaimWorkRepository work;
    private final ClaimRepository claims;
    private final IntakeRepository intake;
    private final PolicyClient policies;
    private final MarketConfigRepository markets;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CoverCheckRetryServiceImpl(ClaimWorkRepository work, ClaimRepository claims, IntakeRepository intake,
                                      PolicyClient policies, MarketConfigRepository markets, UnitOfWork unitOfWork,
                                      Clock clock) {
        this.work = work;
        this.claims = claims;
        this.intake = intake;
        this.policies = policies;
        this.markets = markets;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public CoverCheckRetryResult retryPendingCoverChecks() {
        int triaged = 0, declined = 0, pending = 0;
        List<ClaimId> candidates = work.findPendingCoverChecks(BATCH);
        for (int i = 0; i < candidates.size(); i++) {
            Optional<Claim> found = claims.findById(candidates.get(i));
            if (found.isEmpty() || found.get().status() != ClaimStatus.SUBMITTED) {
                continue;
            }
            Claim claim = found.get();
            CoverVerdict checkCover = checkCover(claim);
            if (checkCover.unavailable()) {
                pending += candidates.size() - i; // Policy system still down: stop hammering it
                break;
            }
            boolean applied = unitOfWork.inTransaction(() -> applyCoverVerdict(claim.id(), checkCover));
            if (applied && checkCover.cover() instanceof CoverCheck.Covered) {
                triaged++;
            } else if (applied) {
                declined++;
            }
        }
        return new CoverCheckRetryResult(triaged, declined, pending);
    }

    private CoverVerdict checkCover(Claim claim) {
        List<PolicySnapshot> held;
        try {
            held = policies.fetchPoliciesOfHolder(claim.claimantId());
        } catch (DomainException ex) {
            if (ex.code() == ErrorCode.POLICY_SYSTEM_UNAVAILABLE) {
                return new CoverVerdict(null, new CoverCheck.Unavailable("policy system unavailable"));
            }
            throw ex;
        }
        Optional<PolicySnapshot> policy = held.stream().filter(p -> p.policyNumber().equals(claim.policyNumber())).findFirst();
        if (policy.isEmpty()) {
            return new CoverVerdict(null, new CoverCheck.NotCovered("POLICY_NOT_FOUND"));
        }
        LocalDate incidentDay = claim.incidentDate().atZone(markets.requireEnabled(claim.market()).timezone()).toLocalDate();
        if (policy.get().lineOfBusiness() != claim.lineOfBusiness() || !policy.get().isInForceOn(incidentDay)) {
            return new CoverVerdict(policy.get(), new CoverCheck.NotCovered("POLICY_NOT_IN_FORCE"));
        }
        // the incident's peril is stored with the incident; fall back to a generic check if it's missing
        String peril = intake.findIncident(claim.id()).map(i -> i.peril()).orElse("UNKNOWN");
        return new CoverVerdict(policy.get(), policies.checkCover(claim.policyNumber(), claim.lineOfBusiness(), incidentDay, peril));
    }

    private boolean applyCoverVerdict(ClaimId claimId, CoverVerdict checkCover) {
        Claim claim = claims.findById(claimId).orElseThrow();
        if (claim.status() != ClaimStatus.SUBMITTED) {
            return false; // withdrawn or handled meanwhile
        }
        switch (checkCover.cover()) {
            case CoverCheck.Covered covered -> claim.confirmCover(Actor.SYSTEM, clock.instant());
            case CoverCheck.NotCovered notCovered -> claim.declineCover(notCovered.reason(), Actor.SYSTEM, clock.instant());
            case CoverCheck.Unavailable unavailable -> {
                return false;
            }
        }
        claims.save(claim);
        if (checkCover.policy() != null && intake.findPolicySnapshot(claimId).isEmpty()) {
            intake.savePolicySnapshot(claimId, checkCover.policy(), clock.instant());
        }
        return true;
    }

    private record CoverVerdict(PolicySnapshot policy, CoverCheck cover) {
        boolean unavailable() {
            return cover instanceof CoverCheck.Unavailable;
        }
    }
}
