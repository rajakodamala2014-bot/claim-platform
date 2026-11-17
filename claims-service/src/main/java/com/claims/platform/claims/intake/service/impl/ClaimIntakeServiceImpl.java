package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.ClaimNumber;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.entity.NewClaim;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.core.service.ClaimantClaimAccessService;
import com.claims.platform.claims.core.service.DocumentService;
import com.claims.platform.claims.intake.repository.IntakeRepository;
import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.entity.Party;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.claims.intake.service.ClaimIntakeService;
import com.claims.platform.claims.platform.repository.IdempotencyRepository;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import com.claims.platform.claims.platform.entity.MarketConfig;
import com.claims.platform.common.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ClaimIntakeServiceImpl implements ClaimIntakeService {
    private final ClaimRepository claims;
    private final IntakeRepository intake;
    private final PolicyClient policies;
    private final MarketConfigRepository markets;
    private final DocumentService documentService;
    private final IdempotencyRepository idempotency;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final ClaimantClaimAccessService claimAccess;

    public ClaimIntakeServiceImpl(ClaimRepository claims, IntakeRepository intake, PolicyClient policies,
                                  MarketConfigRepository markets, DocumentService documentService,
                                  IdempotencyRepository idempotency, UnitOfWork unitOfWork, Clock clock,
                                  ClaimantClaimAccessService claimAccess) {
        this.claims = claims;
        this.intake = intake;
        this.policies = policies;
        this.markets = markets;
        this.documentService = documentService;
        this.idempotency = idempotency;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.claimAccess = claimAccess;
    }

    @Override
    public SubmitClaimResponse submitClaim(Actor actor, UUID idempotencyKey, SubmitClaimCommand command) {
        return registerClaimForCustomer(actor, idempotencyKey, command, null);
    }

    @Override
    public SubmitClaimResponse registerClaimForCustomer(Actor actor, UUID idempotencyKey, SubmitClaimCommand command,
                                                        Set<MarketCode> allowedMarkets) {
        if (idempotencyKey == null) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required");
        }
        String fingerprint = idempotency.fingerprint(command);
        Optional<SubmitClaimResponse> findReplayedSubmission = findReplayedSubmission(actor, idempotencyKey, fingerprint);
        if (findReplayedSubmission.isPresent()) {
            return findReplayedSubmission.get();
        }

        validateSubmission(command);
        Instant now = clock.instant();
        Optional<PolicySnapshot> policy = findPolicy(command);
        MarketCode marketCode = policy.map(PolicySnapshot::market).orElse(command.homeMarket());
        if (marketCode == null) {
            throw new DomainException(ErrorCode.MARKET_NOT_SUPPORTED, "No market is known for this customer");
        }
        if (allowedMarkets != null && !allowedMarkets.contains(marketCode)) {
            throw new DomainException(ErrorCode.FORBIDDEN_MARKET, "You are not assigned to market " + marketCode);
        }
        MarketConfig market = markets.requireEnabled(marketCode);
        LocalDate incidentDay = command.incidentAt().atZone(market.timezone()).toLocalDate();
        policy.ifPresent(p -> p.requireClaimable(command.lineOfBusiness(), incidentDay));
        policy.ifPresent(p -> policies.checkCover(p.policyNumber(), command.lineOfBusiness(), incidentDay, command.incident().peril()));

        try {
            return unitOfWork.inTransaction(() -> {
                Claim claim = createClaim(actor, command, market, now);
                // Every customer is treated as covered. The policy check does not add a status.
                claim.confirmCover(Actor.SYSTEM, now);
                claims.save(claim); // first: documents reference the claim row
                documentService.attachDocumentsToClaim(command.uploadIds(), claim.id(), command.claimantId());
                intake.saveIncident(claim.id(), claim.market(), command.incidentAt(), command.incident());
                intake.saveParties(claim.id(), claim.market(), Party.fromFnol(command.claimantName(),
                        command.contactPhone(), command.contactEmail(), command.incident()));
                policy.ifPresent(p -> intake.savePolicySnapshot(claim.id(), p, now));
                SubmitClaimResponse result = new SubmitClaimResponse(claim.id().value(), claim.number().value(), claim.status().name(),
                        claim.coverStatus().name(), false);
                idempotency.save(actor.id(), idempotencyKey, fingerprint, 201, result);
                return result;
            });
        } catch (IdempotencyRepository.KeyAlreadyUsedException raced) {
            // a concurrent duplicate committed first; our transaction rolled back — answer with its result
            return findReplayedSubmission(actor, idempotencyKey, fingerprint).orElseThrow(() -> raced);
        }
    }

    private Optional<SubmitClaimResponse> findReplayedSubmission(Actor actor, UUID key, String fingerprint) {
        return idempotency.find(actor.id(), key, SubmitClaimResponse.class).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint)) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key " + key + " was already used for a different request");
            }
            return stored.response().asReplay();
        });
    }

    private void validateSubmission(SubmitClaimCommand command) {
        if (command.incident() == null) {
            throw ValidationException.of("incident", "is required");
        }
        if (command.incident().lineOfBusiness() != command.lineOfBusiness()) {
            throw ValidationException.of("incident", "details must be for a " + command.lineOfBusiness() + " claim");
        }
        command.incident().validate();
        if (command.incidentAt().isAfter(clock.instant())) {
            throw ValidationException.of("incidentAt", "cannot be in the future");
        }
        if (command.claimedAmount() == null || !command.claimedAmount().isPositive()) {
            throw ValidationException.of("claimedAmount", "is required and must be greater than zero");
        }
    }

    /** Empty when the Policy system is unavailable (FNOL continues with cover pending). */
    private Optional<PolicySnapshot> findPolicy(SubmitClaimCommand command) {
        List<PolicySnapshot> held;
        try {
            held = policies.fetchPoliciesOfHolder(command.claimantId());
        } catch (DomainException ex) {
            if (ex.code() == ErrorCode.POLICY_SYSTEM_UNAVAILABLE) {
                return Optional.empty();
            }
            throw ex;
        }
        return Optional.of(held.stream()
                .filter(p -> p.policyNumber().equals(command.policyNumber()))
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.POLICY_NOT_FOUND,
                        "Policy " + command.policyNumber() + " not found for this customer")));
    }

    private Claim createClaim(Actor actor, SubmitClaimCommand command, MarketConfig market, Instant now) {
        int year = now.atZone(market.timezone()).getYear();
        ClaimNumber number = ClaimNumber.of(market.market(), command.lineOfBusiness(), year, claims.nextClaimSequence());
        Money claimed = command.claimedAmount();
        NewClaim newClaim = new NewClaim(ClaimId.newId(), number, market.market(), command.lineOfBusiness(),
                command.policyNumber(), command.claimantId(), command.channel(), market.currency(), command.incidentAt(),
                claimed, market.slaTarget());
        return Claim.submit(newClaim, actor, now);
    }

    @Override
    public String withdrawClaim(ClaimId claimId, Actor claimant, String reason) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimAccess.loadOwnedClaim(claimId, claimant.id());
            claim.withdraw(reason, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }

    @Override
    public String appealClaim(ClaimId claimId, Actor claimant, String reason) {
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimAccess.loadOwnedClaim(claimId, claimant.id());
            claim.appeal(reason, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }
}
