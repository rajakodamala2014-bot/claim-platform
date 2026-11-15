package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.ClaimWorkQueries;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.intake.domain.CoverCheck;
import com.claims.platform.claims.intake.domain.PolicySnapshot;
import com.claims.platform.claims.platform.application.MarketConfigs;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cover-check retry job (every 10 min): claims saved while the Policy system was unreachable get their cover
 * check now -> ClaimTriaged, or CoverDeclined (still triaged, flagged for the handler). If the Policy system
 * is still down the run stops early and the claims wait for the next run. Remote calls happen outside
 * transactions; each claim is re-read and re-checked inside its own transaction before it is changed.
 */
public class RetryCoverChecks {
    private static final int BATCH = 100;

    private final ClaimWorkQueries work;
    private final ClaimRepository claims;
    private final IntakeRecords intake;
    private final PolicyGateway policies;
    private final MarketConfigs markets;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RetryCoverChecks(ClaimWorkQueries work, ClaimRepository claims, IntakeRecords intake, PolicyGateway policies,
                            MarketConfigs markets, UnitOfWork unitOfWork, Clock clock) {
        this.work = work;
        this.claims = claims;
        this.intake = intake;
        this.policies = policies;
        this.markets = markets;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public record Result(int triaged, int declined, int stillPending) {
    }

    public Result runOnce() {
        int triaged = 0, declined = 0, pending = 0;
        List<ClaimId> candidates = work.pendingCoverChecks(BATCH);
        for (int i = 0; i < candidates.size(); i++) {
            Optional<Claim> found = claims.findById(candidates.get(i));
            if (found.isEmpty() || found.get().status() != ClaimStatus.SUBMITTED) {
                continue;
            }
            Claim claim = found.get();
            Verdict verdict = verdict(claim);
            if (verdict.unavailable()) {
                pending += candidates.size() - i; // Policy system still down: stop hammering it
                break;
            }
            boolean applied = unitOfWork.inTransaction(() -> apply(claim.id(), verdict));
            if (applied && verdict.cover() instanceof CoverCheck.Covered) {
                triaged++;
            } else if (applied) {
                declined++;
            }
        }
        return new Result(triaged, declined, pending);
    }

    private Verdict verdict(Claim claim) {
        List<PolicySnapshot> held;
        try {
            held = policies.policiesOf(claim.claimantId());
        } catch (DomainException ex) {
            if (ex.code() == ErrorCode.POLICY_SYSTEM_UNAVAILABLE) {
                return new Verdict(null, new CoverCheck.Unavailable("policy system unavailable"));
            }
            throw ex;
        }
        Optional<PolicySnapshot> policy = held.stream().filter(p -> p.policyNumber().equals(claim.policyNumber())).findFirst();
        if (policy.isEmpty()) {
            return new Verdict(null, new CoverCheck.NotCovered("POLICY_NOT_FOUND"));
        }
        LocalDate incidentDay = claim.incidentDate().atZone(markets.requireEnabled(claim.market()).timezone()).toLocalDate();
        if (policy.get().lineOfBusiness() != claim.lineOfBusiness() || !policy.get().isInForceOn(incidentDay)) {
            return new Verdict(policy.get(), new CoverCheck.NotCovered("POLICY_NOT_IN_FORCE"));
        }
        // the incident's peril is stored with the incident; fall back to a generic check if it's missing
        String peril = intake.findIncident(claim.id()).map(i -> i.peril()).orElse("UNKNOWN");
        return new Verdict(policy.get(), policies.checkCover(claim.policyNumber(), claim.lineOfBusiness(), incidentDay, peril));
    }

    private boolean apply(ClaimId claimId, Verdict verdict) {
        Claim claim = claims.findById(claimId).orElseThrow();
        if (claim.status() != ClaimStatus.SUBMITTED) {
            return false; // withdrawn or handled meanwhile
        }
        switch (verdict.cover()) {
            case CoverCheck.Covered covered -> claim.confirmCover(Actor.SYSTEM, clock.instant());
            case CoverCheck.NotCovered notCovered -> claim.declineCover(notCovered.reason(), Actor.SYSTEM, clock.instant());
            case CoverCheck.Unavailable unavailable -> {
                return false;
            }
        }
        claims.save(claim);
        if (verdict.policy() != null && intake.findPolicySnapshot(claimId).isEmpty()) {
            intake.savePolicySnapshot(claimId, verdict.policy(), clock.instant());
        }
        return true;
    }

    private record Verdict(PolicySnapshot policy, CoverCheck cover) {
        boolean unavailable() {
            return cover instanceof CoverCheck.Unavailable;
        }
    }
}
