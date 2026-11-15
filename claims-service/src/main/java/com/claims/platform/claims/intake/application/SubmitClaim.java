package com.claims.platform.claims.intake.application;

import com.claims.platform.claims.core.application.AttachDocuments;
import com.claims.platform.claims.core.application.ClaimRepository;
import com.claims.platform.claims.core.application.UnitOfWork;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.Claim;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ClaimNumber;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.NewClaim;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.intake.domain.CoverCheck;
import com.claims.platform.claims.intake.domain.IncidentDetails;
import com.claims.platform.claims.intake.domain.Party;
import com.claims.platform.claims.intake.domain.PolicySnapshot;
import com.claims.platform.claims.platform.application.IdempotencyStore;
import com.claims.platform.claims.platform.application.MarketConfigs;
import com.claims.platform.claims.platform.domain.MarketConfig;
import com.claims.platform.common.money.Money;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * C3 (customer) / S1 (staff on the customer's behalf): FNOL.
 * <ol>
 *     <li>Idempotency-Key replay check.</li>
 *     <li>Validate incident details per line of business.</li>
 *     <li>Outside any transaction: fetch the policy and run the cover check (2 s timeouts). If the Policy system
 *     is unavailable the claim is still created, with cover pending (retried by a job).</li>
 *     <li>One transaction: claim + incident + parties + policy snapshot + documents + events/outbox +
 *     idempotency record.</li>
 * </ol>
 */
public class SubmitClaim {
    private final ClaimRepository claims;
    private final IntakeRecords intake;
    private final PolicyGateway policies;
    private final MarketConfigs markets;
    private final AttachDocuments attachDocuments;
    private final IdempotencyStore idempotency;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SubmitClaim(ClaimRepository claims, IntakeRecords intake, PolicyGateway policies, MarketConfigs markets,
                       AttachDocuments attachDocuments, IdempotencyStore idempotency, UnitOfWork unitOfWork, Clock clock) {
        this.claims = claims;
        this.intake = intake;
        this.policies = policies;
        this.markets = markets;
        this.attachDocuments = attachDocuments;
        this.idempotency = idempotency;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /**
     * @param claimantId   whose claim it is: the caller for C3, the customer for S1
     * @param homeMarket   market to use if the Policy system can't tell us (claimant's market from the token)
     */
    public record Command(
            String claimantId,
            String claimantName,
            MarketCode homeMarket,
            Channel channel,
            String policyNumber,
            LineOfBusiness lineOfBusiness,
            Instant incidentAt,
            IncidentDetails incident,
            Money claimedAmount,
            List<UUID> uploadIds,
            String contactPhone,
            String contactEmail
    ) {
        public Command {
            uploadIds = uploadIds == null ? List.of() : List.copyOf(uploadIds);
        }
    }

    public record Result(UUID claimId, String claimNumber, String status, String coverStatus, boolean replayed) {
        Result asReplay() {
            return new Result(claimId, claimNumber, status, coverStatus, true);
        }
    }

    public Result submit(Actor actor, UUID idempotencyKey, Command command) {
        return submit(actor, idempotencyKey, command, null);
    }

    /**
     * @param allowedMarkets for staff (S1): the markets the caller may work in; null for a customer (C3)
     */
    public Result submit(Actor actor, UUID idempotencyKey, Command command, Set<MarketCode> allowedMarkets) {
        if (idempotencyKey == null) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required");
        }
        String fingerprint = idempotency.fingerprint(command);
        Optional<Result> replay = replay(actor, idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }

        validate(command);
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
        CoverCheck cover = policy
                .map(p -> policies.checkCover(p.policyNumber(), command.lineOfBusiness(), incidentDay, command.incident().peril()))
                .orElseGet(() -> new CoverCheck.Unavailable("Policy system unavailable"));

        try {
            return unitOfWork.inTransaction(() -> {
                Claim claim = create(actor, command, market, now);
                switch (cover) {
                    case CoverCheck.Covered covered -> claim.confirmCover(Actor.SYSTEM, now);
                    case CoverCheck.NotCovered notCovered -> claim.declineCover(notCovered.reason(), Actor.SYSTEM, now);
                    case CoverCheck.Unavailable unavailable -> { /* stays SUBMITTED, cover PENDING; retry job */ }
                }
                claims.save(claim); // first: documents reference the claim row
                attachDocuments.attach(command.uploadIds(), claim.id(), command.claimantId());
                intake.saveIncident(claim.id(), claim.market(), command.incidentAt(), command.incident());
                intake.saveParties(claim.id(), claim.market(), Party.fromFnol(command.claimantName(),
                        command.contactPhone(), command.contactEmail(), command.incident()));
                policy.ifPresent(p -> intake.savePolicySnapshot(claim.id(), p, now));
                Result result = new Result(claim.id().value(), claim.number().value(), claim.status().name(),
                        claim.coverStatus().name(), false);
                idempotency.save(actor.id(), idempotencyKey, fingerprint, 201, result);
                return result;
            });
        } catch (IdempotencyStore.KeyAlreadyUsedException raced) {
            // a concurrent duplicate committed first; our transaction rolled back — answer with its result
            return replay(actor, idempotencyKey, fingerprint).orElseThrow(() -> raced);
        }
    }

    private Optional<Result> replay(Actor actor, UUID key, String fingerprint) {
        return idempotency.find(actor.id(), key, Result.class).map(stored -> {
            if (!stored.fingerprint().equals(fingerprint)) {
                throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key " + key + " was already used for a different request");
            }
            return stored.response().asReplay();
        });
    }

    private void validate(Command command) {
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
        if (command.claimedAmount() != null && command.claimedAmount().isNegative()) {
            throw ValidationException.of("claimedAmount", "cannot be negative");
        }
    }

    /** Empty when the Policy system is unavailable (FNOL continues with cover pending). */
    private Optional<PolicySnapshot> findPolicy(Command command) {
        List<PolicySnapshot> held;
        try {
            held = policies.policiesOf(command.claimantId());
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

    private Claim create(Actor actor, Command command, MarketConfig market, Instant now) {
        int year = now.atZone(market.timezone()).getYear();
        ClaimNumber number = ClaimNumber.of(market.market(), command.lineOfBusiness(), year, claims.nextClaimSequence());
        Money claimed = command.claimedAmount();
        NewClaim newClaim = new NewClaim(ClaimId.newId(), number, market.market(), command.lineOfBusiness(),
                command.policyNumber(), command.claimantId(), command.channel(), market.currency(), command.incidentAt(),
                claimed, market.slaTarget());
        return Claim.submit(newClaim, actor, now);
    }
}
