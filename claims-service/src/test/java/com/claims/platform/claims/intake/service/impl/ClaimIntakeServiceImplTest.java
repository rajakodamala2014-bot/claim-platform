package com.claims.platform.claims.intake.service.impl;

import com.claims.platform.claims.core.service.impl.ClaimantClaimAccessServiceImpl;
import com.claims.platform.claims.core.service.impl.DocumentServiceImpl;
import com.claims.platform.claims.intake.repository.IntakeRepository;
import com.claims.platform.claims.intake.client.PolicyClient;
import com.claims.platform.claims.intake.service.ClaimIntakeService;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.claims.core.service.DocumentService;
import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.repository.DocumentRepository;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.entity.CoverStatus;
import com.claims.platform.claims.core.entity.Document;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.intake.entity.CoverCheck;
import com.claims.platform.claims.intake.entity.IncidentDetails;
import com.claims.platform.claims.intake.entity.MotorIncident;
import com.claims.platform.claims.intake.entity.Party;
import com.claims.platform.claims.intake.entity.PolicySnapshot;
import com.claims.platform.claims.platform.repository.IdempotencyRepository;
import com.claims.platform.claims.platform.repository.MarketConfigRepository;
import com.claims.platform.claims.platform.entity.MarketConfig;
import com.claims.platform.common.money.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FNOL use case with in-memory adapters: the orchestration rules, without HTTP or a database. */
class ClaimIntakeServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");
    private static final Actor PRIYA = Actor.claimant("cust-priya");
    private static final MarketCode SG = MarketCode.of("SG");

    private final InMemoryClaims claims = new InMemoryClaims();
    private final FakeIntake intake = new FakeIntake();
    private final FakePolicies policies = new FakePolicies();
    private final InMemoryDocuments documents = new InMemoryDocuments();
    private final InMemoryIdempotency idempotency = new InMemoryIdempotency();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ClaimIntakeService claimIntakeService = new ClaimIntakeServiceImpl(claims, intake, policies, new SgOnly(),
            new DocumentServiceImpl(documents, clock), idempotency, new DirectUnitOfWork(), clock,
            new ClaimantClaimAccessServiceImpl(claims));

    @Test
    void coveredClaimIsTriagedNumberedAndStoredWithItsRecords() {
        SubmitClaimResponse result = claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of()));

        assertThat(result.status()).isEqualTo("TRIAGED");
        assertThat(result.coverStatus()).isEqualTo("CONFIRMED");
        assertThat(result.claimNumber()).isEqualTo("SG-MTR-2026-000001");
        assertThat(result.replayed()).isFalse();
        Claim saved = claims.only();
        assertThat(saved.currency()).isEqualTo("SGD");
        assertThat(saved.slaDueAt()).isEqualTo(NOW.plusSeconds(48 * 3600));
        assertThat(intake.incidents).containsKey(saved.id());
        assertThat(intake.policies).containsKey(saved.id());
        assertThat(intake.parties.get(saved.id())).extracting(Party::role)
                .containsExactly(Party.Role.CLAIMANT, Party.Role.THIRD_PARTY);
        assertThat(policies.lastCoverCheckDate).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void incidentDayIsJudgedInTheMarketsTimezone() {
        // 20:00 UTC on the 13th is 04:00 on the 14th in Singapore
        ClaimIntakeService.SubmitClaimCommand lateNight = new ClaimIntakeService.SubmitClaimCommand("cust-priya", "Priya", SG, Channel.APP, "SG-MTR-POL-1001",
                LineOfBusiness.MOTOR, Instant.parse("2026-11-13T20:00:00Z"), motor(), null, List.of(), null, null);

        claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), lateNight);

        assertThat(policies.lastCoverCheckDate).isEqualTo(LocalDate.of(2026, 11, 14));
    }

    @Test
    void notCoveredClaimIsStillTriagedButFlagged() {
        policies.cover = new CoverCheck.NotCovered("PERIL_NOT_COVERED");

        SubmitClaimResponse result = claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of()));

        assertThat(result.status()).isEqualTo("TRIAGED");
        assertThat(result.coverStatus()).isEqualTo("DECLINED");
    }

    @Test
    void coverCheckTimeoutNeverBlocksFnol() {
        policies.cover = new CoverCheck.Unavailable("timeout");

        SubmitClaimResponse result = claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of()));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.coverStatus()).isEqualTo("PENDING");
    }

    @Test
    void policySystemDownStillCreatesTheClaimInTheCustomersHomeMarket() {
        policies.down = true;

        SubmitClaimResponse result = claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of()));

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.coverStatus()).isEqualTo("PENDING");
        assertThat(claims.only().market()).isEqualTo(SG);
        assertThat(intake.policies).isEmpty();
        assertThat(policies.coverChecks).isZero();
    }

    @Test
    void sameKeyAndBodyReplaysTheOriginalResult() {
        UUID key = UUID.randomUUID();
        ClaimIntakeService.SubmitClaimCommand command = command("SG-MTR-POL-1001", List.of());

        SubmitClaimResponse first = claimIntakeService.submitClaim(PRIYA, key, command);
        SubmitClaimResponse again = claimIntakeService.submitClaim(PRIYA, key, command);

        assertThat(again.claimId()).isEqualTo(first.claimId());
        assertThat(again.claimNumber()).isEqualTo(first.claimNumber());
        assertThat(again.replayed()).isTrue();
        assertThat(claims.byId).hasSize(1);
    }

    @Test
    void sameKeyWithADifferentBodyIsRejected() {
        UUID key = UUID.randomUUID();
        claimIntakeService.submitClaim(PRIYA, key, command("SG-MTR-POL-1001", List.of()));

        expectCode(() -> claimIntakeService.submitClaim(PRIYA, key, command("SG-MTR-POL-1500", List.of())), ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void keysAreScopedPerCaller() {
        UUID key = UUID.randomUUID();
        claimIntakeService.submitClaim(PRIYA, key, command("SG-MTR-POL-1001", List.of()));

        Actor other = Actor.claimant("cust-tan");
        policies.held.put("cust-tan", List.of(policy("SG-MTR-POL-7", "cust-tan", "ACTIVE")));
        ClaimIntakeService.SubmitClaimCommand theirs = new ClaimIntakeService.SubmitClaimCommand("cust-tan", "Tan", SG, Channel.APP, "SG-MTR-POL-7",
                LineOfBusiness.MOTOR, Instant.parse("2026-11-13T09:00:00Z"), motor(), null, List.of(), null, null);

        assertThat(claimIntakeService.submitClaim(other, key, theirs).replayed()).isFalse();
        assertThat(claims.byId).hasSize(2);
    }

    @Test
    void missingKeyIsRejected() {
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, null, command("SG-MTR-POL-1001", List.of())), ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    void unknownExpiredOrWrongLinePolicies() {
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("NOPE", List.of())), ErrorCode.POLICY_NOT_FOUND);
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1999", List.of())),
                ErrorCode.POLICY_NOT_IN_FORCE);
        policies.held.get("cust-priya").add(policy("SG-PRP-POL-2001", "cust-priya", "ACTIVE", LineOfBusiness.PROPERTY));
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-PRP-POL-2001", List.of())),
                ErrorCode.VALIDATION_FAILED);
        assertThat(claims.byId).isEmpty();
    }

    @Test
    void invalidIncidentDetailsListEveryOffendingField() {
        ClaimIntakeService.SubmitClaimCommand bad = new ClaimIntakeService.SubmitClaimCommand("cust-priya", "Priya Nair", SG, Channel.APP, "SG-MTR-POL-1001",
                LineOfBusiness.MOTOR, Instant.parse("2026-11-13T09:00:00Z"),
                new MotorIncident("METEOR", "", "x", null, null, null, null, List.of()), null, List.of(), null, null);

        assertThatThrownBy(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), bad))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).violations())
                        .extracting(ValidationException.FieldViolation::field)
                        .contains("incident.peril", "incident.description", "incident.vehicleRegistration",
                                "incident.driverName", "incident.damageDescription"));
    }

    @Test
    void incidentInTheFutureOrForeignCurrencyIsRejected() {
        ClaimIntakeService.SubmitClaimCommand future = new ClaimIntakeService.SubmitClaimCommand("cust-priya", "Priya", SG, Channel.APP, "SG-MTR-POL-1001",
                LineOfBusiness.MOTOR, NOW.plusSeconds(60), motor(), null, List.of(), null, null);
        ClaimIntakeService.SubmitClaimCommand hkd = new ClaimIntakeService.SubmitClaimCommand("cust-priya", "Priya", SG, Channel.APP, "SG-MTR-POL-1001",
                LineOfBusiness.MOTOR, Instant.parse("2026-11-13T09:00:00Z"), motor(), Money.of("100", "HKD"), List.of(), null, null);

        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), future), ErrorCode.VALIDATION_FAILED);
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), hkd), ErrorCode.CURRENCY_MISMATCH);
    }

    @Test
    void uploadsAreAttachedOnlyWhenOwnedAndComplete() {
        Document mine = Document.requested(UUID.randomUUID(), "cust-priya", "photo.jpg", "image/jpeg", 100, NOW).uploaded(100, NOW);
        Document pending = Document.requested(UUID.randomUUID(), "cust-priya", "p.jpg", "image/jpeg", 100, NOW);
        Document theirs = Document.requested(UUID.randomUUID(), "cust-tan", "t.jpg", "image/jpeg", 100, NOW).uploaded(100, NOW);
        List.of(mine, pending, theirs).forEach(documents::save);

        claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of(mine.id())));
        assertThat(documents.findById(mine.id()).orElseThrow().claimId()).isEqualTo(claims.only().id());

        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of(pending.id()))),
                ErrorCode.UPLOAD_REJECTED);
        expectCode(() -> claimIntakeService.submitClaim(PRIYA, UUID.randomUUID(), command("SG-MTR-POL-1001", List.of(theirs.id()))),
                ErrorCode.UPLOAD_NOT_FOUND);
    }

    // ------------------------------------------------------------------ helpers

    private static ClaimIntakeService.SubmitClaimCommand command(String policyNumber, List<UUID> uploads) {
        return new ClaimIntakeService.SubmitClaimCommand("cust-priya", "Priya Nair", SG, Channel.APP, policyNumber, LineOfBusiness.MOTOR,
                Instant.parse("2026-11-13T09:00:00Z"), motor(), Money.of("6000", "SGD"), uploads, "+6591234567", null);
    }

    private static IncidentDetails motor() {
        return new MotorIncident("COLLISION", "Rear-ended at lights", "Orchard Rd", "SBA1234X", "Priya Nair",
                "Rear bumper", null, List.of(new MotorIncident.ThirdParty("John Lim", "SGX9876Z", "NTUC Income", null)));
    }

    private static PolicySnapshot policy(String number, String holder, String status) {
        return policy(number, holder, status, LineOfBusiness.MOTOR);
    }

    private static PolicySnapshot policy(String number, String holder, String status, LineOfBusiness lob) {
        return new PolicySnapshot(number, holder, "Priya Nair", SG, lob, "Motor", status, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), "SGD", 3_000_000L, 50_000L, List.of("COLLISION"));
    }

    private static void expectCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo(code);
    }

    static final class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    }

    static final class InMemoryClaims implements ClaimRepository {
        final Map<ClaimId, Claim> byId = new HashMap<>();
        long sequence;

        Claim only() {
            assertThat(byId).hasSize(1);
            return byId.values().iterator().next();
        }

        @Override
        public Optional<Claim> findById(ClaimId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(Claim claim) {
            claim.pullPendingEvents();
            claim.markSaved(claim.version() + 1);
            byId.put(claim.id(), claim);
        }

        @Override
        public long nextClaimSequence() {
            return ++sequence;
        }
    }

    static final class FakeIntake implements IntakeRepository {
        final Map<ClaimId, IncidentDetails> incidents = new HashMap<>();
        final Map<ClaimId, List<Party>> parties = new HashMap<>();
        final Map<ClaimId, PolicySnapshot> policies = new HashMap<>();

        @Override
        public void saveIncident(ClaimId claimId, MarketCode market, Instant occurredAt, IncidentDetails incident) {
            incidents.put(claimId, incident);
        }

        @Override
        public void saveParties(ClaimId claimId, MarketCode market, List<Party> list) {
            parties.put(claimId, list);
        }

        @Override
        public void savePolicySnapshot(ClaimId claimId, PolicySnapshot policy, Instant capturedAt) {
            policies.put(claimId, policy);
        }

        @Override
        public Optional<IncidentDetails> findIncident(ClaimId claimId) {
            return Optional.ofNullable(incidents.get(claimId));
        }

        @Override
        public Optional<PolicySnapshot> findPolicySnapshot(ClaimId claimId) {
            return Optional.ofNullable(policies.get(claimId));
        }

        @Override
        public List<Party> findParties(ClaimId claimId) {
            return parties.getOrDefault(claimId, List.of());
        }
    }

    static final class FakePolicies implements PolicyClient {
        final Map<String, List<PolicySnapshot>> held = new HashMap<>(Map.of("cust-priya", new ArrayList<>(List.of(
                policy("SG-MTR-POL-1001", "cust-priya", "ACTIVE"),
                policy("SG-MTR-POL-1500", "cust-priya", "ACTIVE"),
                policy("SG-MTR-POL-1999", "cust-priya", "EXPIRED")))));
        CoverCheck cover = new CoverCheck.Covered();
        boolean down;
        int coverChecks;
        LocalDate lastCoverCheckDate;

        @Override
        public List<PolicySnapshot> fetchPoliciesOfHolder(String holderId) {
            if (down) {
                throw new DomainException(ErrorCode.POLICY_SYSTEM_UNAVAILABLE, "down");
            }
            return held.getOrDefault(holderId, List.of());
        }

        @Override
        public CoverCheck checkCover(String policyNumber, LineOfBusiness lob, LocalDate incidentDate, String peril) {
            coverChecks++;
            lastCoverCheckDate = incidentDate;
            return cover;
        }
    }

    static final class SgOnly implements MarketConfigRepository {
        @Override
        public Optional<MarketConfig> find(MarketCode market) {
            return market.equals(SG)
                    ? Optional.of(new MarketConfig(SG, "Singapore", "SGD", ZoneId.of("Asia/Singapore"), 48, 14, 14, List.of("en"), true))
                    : Optional.empty();
        }
    }

    static final class InMemoryDocuments implements DocumentRepository {
        final Map<UUID, Document> byId = new HashMap<>();

        @Override
        public void save(Document document) {
            byId.put(document.id(), document);
        }

        @Override
        public Optional<Document> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Document> findByClaimId(ClaimId claimId) {
            return byId.values().stream().filter(d -> claimId.equals(d.claimId())).toList();
        }
    }

    static final class InMemoryIdempotency implements IdempotencyRepository {
        final Map<String, Stored<?>> stored = new HashMap<>();

        @Override
        public String fingerprint(Object request) {
            return Integer.toHexString(request.toString().hashCode());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<Stored<T>> find(String callerId, UUID key, Class<T> type) {
            return Optional.ofNullable((Stored<T>) stored.get(callerId + key));
        }

        @Override
        public void save(String callerId, UUID key, String fingerprint, int status, Object response) {
            if (stored.putIfAbsent(callerId + key, new Stored<>(fingerprint, status, response)) != null) {
                throw new KeyAlreadyUsedException(key);
            }
        }
    }
}
