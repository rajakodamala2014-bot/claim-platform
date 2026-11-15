# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Claims platform backend for MOTOR and PROPERTY claims across APAC markets (SG, HK, MY, TH, ID, AU — configurable). Java 21, Spring Boot 3.4, PostgreSQL 16, Flyway, Kafka (Redpanda locally), Maven multi-module mono-repo, plus a React claimant portal (`claims-ui`, npm).

Four deployable services: `gateway-service` (the public entry point) in front of `claims-service` (source of truth, the only writer of claims), `insights-service` (read models) and `notification-service`, which communicate only through Kafka. Everything else (identity provider, Policy system, Payments, file storage, e-mail/SMS/push) is an external system simulated locally. `README.md` has the architecture diagrams, endpoint list (C1–C10, S1–S12, I1–I7, N1–N7), the trade-offs table and the dev personas; `http/happy-path.http` walks FNOL → settlement.

## Commands

```sh
./scripts/ci.sh                          # what CI runs: ./mvnw -B -ntp verify (unit + ArchUnit + Testcontainers ITs)
./mvnw test                              # fast: unit/slice/ArchUnit/embedded-Kafka tests (surefire, *Test)
./mvnw verify                            # also Testcontainers integration tests (failsafe, *IT)
./mvnw -pl claims-service -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false   # one test class
./mvnw -pl claims-service -am verify -Dit.test=ClaimsServiceIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false  # one IT

docker compose up -d --build             # whole stack; gateway on http://localhost:8000
docker compose up -d postgres redpanda kafka-init redpanda-console policy-system payments-simulator   # infra only, run services from IDE
SPRING_PROFILES_ACTIVE=dev ./mvnw -pl claims-service -am spring-boot:run   # dev profile supplies the JWT secret + field key; without it startup fails
docker compose config -q                 # validate compose without a Docker daemon

cd claims-ui && npm install && npm run dev   # portal on :5173, proxies to the gateway on :8000
npm test                                 # vitest;  npx vitest run src/format.test.ts  for one file
npm run typecheck                        # tsc -b (there is no lint script);  npm run build = typecheck + vite build
```

Use `-pl <module> -am` (not `-f module/pom.xml`) so sibling SNAPSHOT modules resolve from the reactor. On Windows use `.\mvnw.cmd` from PowerShell (`$env:SPRING_PROFILES_ACTIVE='dev'`) or run the commands as written in Git Bash; `scripts/ci.sh` needs bash.

CI (`.github/workflows/ci.yml`) runs only `REQUIRE_DOCKER=1 ./scripts/ci.sh` on JDK 21 — `claims-ui` is not built or tested there, so run its `npm test` / `npm run build` yourself after UI changes.

Integration tests get PostgreSQL 16 from `claims-test-support`'s `TestPostgres.registerFreshDatabase(registry)` in a `@DynamicPropertySource`: Testcontainers when Docker is available (CI, `REQUIRE_DOCKER=1`), otherwise Zonky embedded Postgres 16 — so ITs **run** locally without Docker (`-Dtest.postgres=testcontainers` forbids the fallback). One server per JVM, fresh database per test class. ITs use `@ActiveProfiles("dev")` (dev JWT secret + upload signing secret). Kafka likewise: `TestKafka.register(registry, topics...)` (Testcontainers `apache/kafka` with Docker, in-JVM KRaft broker without) — it also sets a **unique `spring.kafka.consumer.group-id` per test context**, because cached Spring contexts sharing a group split partitions and steal each other's messages. So `@KafkaListener`s must not hard-code `groupId`; take it from `spring.kafka.consumer.group-id`. With several cached contexts alive, a message may be consumed (and dead-lettered) once per context — assert on content, not exact DLQ counts. HTTP stubs: WireMock standalone; `ClaimantJourneyIT` loads `docker/policy-system` mappings so the compose stub and tests share one contract.

## Local stack (docker-compose.yml)

| Name | Port (host) | What |
|---|---|---|
| gateway | 8000 | our `gateway-service` (Spring Cloud Gateway Server MVC): routes, validates JWTs, rate-limits, circuit-breaks, mints X-Correlation-Id, and proxies `/` to the `claims-ui` container (unrouted `/claimant`, `/staff`, `/admin`, `/internal`, `/actuator`, `/dev` paths are 404). Actuator on management port 8001 (not published). Services still validate JWTs themselves. |
| claims-service / insights-service / notification-service | 8080 / 8081 / 8082 | our services; DBs `claims` / `insights` / `notification` on postgres:5432 (user claims/claims) |
| payments-simulator | 8083 | fake Payments system: consumes `payments.settlement-requests`, answers on `payments.payment-results`. Account ending `0000` → FAILED. |
| policy-system | 8089 | WireMock (`docker/policy-system`) — **defines the Policy API contract**: `GET /api/v1/policies?holderId=`, `POST /api/v1/policies/{no}/cover-checks`. `SG-MTR-POL-1500` delays 3s to exercise the 2s timeout → cover pending. |
| redpanda / console | 19092 / 8090 | Kafka API; topics created by `kafka-init` from `docker/kafka/create-topics.sh` |
| identity provider | — | dev token issuer in claims-service (`dev` profile), `POST /dev/token`, HS256 key `DEV_JWT_SECRET` shared by all services |

All Spring modules build from one `docker/service.Dockerfile` (`--build-arg SERVICE=<module>`). Document storage uses the filesystem adapter (volume `documents`); MinIO was dropped (no longer published on Docker Hub) — an S3 adapter can plug into the same `DocumentStorage` port.

## Modules

- `claims-common` — framework-free shared contracts (no Spring/Jackson): `EventEnvelope`, `EventTypes` (all event names, grouped by topic), `Topics` (+ `.dlq` convention), `SettlementRequest`/`PaymentResult` (payments topic contract; `instructionId` is unique per attempt), `Headers`. Domain code may depend on it.
- `claims-web` — shared Spring plumbing via `META-INF/spring/...AutoConfiguration.imports`: `CorrelationIdFilter`, `MaskingJsonLogFormatter` + `PiiMasker` (enabled by `logging.structured.format.console`).
- `claims-messaging` — shared Kafka consumer policy (auto-configured): `ProcessedEvents.markProcessed(consumer, messageId)` (call in the same transaction as the side effects; table from `platform.messaging.processed-event-table`), and a `CommonErrorHandler` giving every listener 5 attempts then `<topic>.dlq` (key preserved); throw `MalformedMessageException` to skip retries.
- `claims-architecture` — reusable ArchUnit rules (`CleanArchitectureRules`), test-scoped in every service.
- `claims-test-support` — `TestPostgres` / `TestKafka` (see Commands), test-scoped in every service and `e2e-tests`.
- `claims-service` — modular monolith: `core` (Claim aggregate + state machine), `intake`, `handling`, `settlement`, `platform`, each `domain / application / infrastructure / api`; one Flyway schema per module (`platform` holds flyway history).
- `gateway-service` — Spring Cloud Gateway **Server MVC** (servlet, so it reuses `claims-web`; not the reactive flavor). One flat package, no domain, not under the clean-architecture rules:
  - `Routes` is the routing table (order = precedence; Notification and Insights paths before the Claims catch-alls). A new public path needs a route here, a rule in `GatewaySecurityConfig` if its surface/role is new, and a row in `RoutingTest`.
  - Filter order on API routes, outermost first: body limit (413) → `RateLimiter` (Bucket4j in memory, key = token `sub` else client IP) → `UpstreamBreakers` (Resilience4j, one per upstream; failure = exception or 502/503/504; no retries) → response headers → request headers → proxy. `EdgeHeaders.apiResponseHeaders` must stay directly around the proxy: gateway-built responses have read-only headers.
  - Gateway errors go through `GatewayProblems` (same problem+json shape as the services). Upstream URLs: `gateway.upstreams.*` (`CLAIMS_URL`, `INSIGHTS_URL`, `NOTIFICATION_URL`, `UI_URL`). `/dev/token` is routed only with `gateway.dev-token-route=true` (dev profile).
  - HTTP client is the JDK client pinned to HTTP/1.1 (same h2c problem as the Policy adapter), which cannot set `Host`: upstreams get `X-Forwarded-Host/Proto/For` written by the gateway instead (caller-supplied values are dropped).
  - Tests extend `GatewayTestBase` (real gateway on a random port, four WireMock upstreams, rate limits effectively off); buckets and breakers live as long as the Spring context, so rate-limit tests use their own subjects and breakers are reset before each test.
- `insights-service` — read-only CQRS read models from `claims.claim-events`; publishes `insights.alerts`. One `insights.claim_summary` row per claim, folded by `domain.ClaimSummary.apply(EventEnvelope)` (unknown events ignored). `application.StaffInsights` owns access rules (markets, team, supervisor-only for I3–I7); `JdbcInsightsQueries` holds the SQL. SLA runs until a decision and pauses in AWAITING_INFO; `CheckSlaAlerts` (5 min, advisory-lock leader) publishes SlaAtRisk/SlaBreached once per (claim, kind, due date) with deterministic event ids.
- `notification-service` — consumes `claims.claim-events` + `insights.alerts`. `PlanNotifications` (in the consumer transaction) decides recipients via `domain.NotificationRules` and renders per-language templates into PENDING `notification` rows; a dispatcher (1 s, `FOR UPDATE SKIP LOCKED`) sends them through fake providers with 3 attempts + backoff, then FAILED. Languages `en, zh, zh-Hant (HK), ms, th, id`; template lookup falls back to the base language, then English. Keeps its own `claim_contact` projection (JDBC upsert) and seeded `staff_directory`/`preference`/`template` data (V3) — never calls Claims. Alerts may arrive before claim events (different topics): staff notifications are built from the alert's own data.
- `e2e-tests` — `CrossServiceJourneyIT` starts the packaged jars (claims, insights, notification, payments-simulator) as processes against shared TestPostgres/TestKafka/WireMock and walks FNOL → settlement. Needs `-am` (it depends on the services with `<type>pom</type>` only for reactor ordering); child logs in `e2e-tests/target/e2e-logs`.
- `payments-simulator` — local stand-in only; not subject to the clean-architecture rules.
- `claims-ui` — claimant portal, React 18 + Vite + TypeScript (npm, **not** in the Maven reactor). Own compose service `claims-ui` (`claims-ui/Dockerfile`: Node build → nginx with SPA fallback, `claims-ui/docker/nginx.conf`; host port 3000), reached through the gateway's `location /`; client-side routes `/claims`, `/claims/:id`, `/notifications`, `/settings`. Talks only to `/claimant/v1/**` + `/dev/token`, same origin (Vite dev server proxies to :8000). `cd claims-ui && npm run dev | npm test | npm run build`. See `claims-ui/README.md`.

## Architecture rules (enforced by each service's `ArchitectureTest`)

- `..domain..` may depend only on `java..`, other `..domain..` code and `com.claims.platform.common..`.
- Dependencies point inwards: api/infrastructure → application → domain; api and infrastructure never depend on each other.
- In claims-service, `intake`, `handling`, `settlement` must not depend on each other (share only `core`, `platform`, events).
- ArchitectureTests are **plain JUnit tests calling `rule.check(CleanArchitectureRules.productionClasses(pkg))`** — don't use `@AnalyzeClasses`/`@ArchTest`: that engine silently ran zero rules under surefire here.

## claims-service conventions

- Persistence is plain JDBC (`JdbcClient`), no JPA: `core.infrastructure.JdbcClaimRepository` maps the aggregate (claim row with `version` check → `CONCURRENT_UPDATE`; children upserted; ledger rows insert-only; events → `core.claim_event` + `platform.outbox` in the same transaction; `SettlementInstructed` also writes a `SettlementRequest` to `payments.settlement-requests`). pgjdbc needs `OffsetDateTime` (use `Jdbc.ts/instant`), arrays via `SqlArrayValue`. DB triggers make `reserve_movement` and `claim_event` append-only.
- Use cases are plain classes wired in per-module `*Config` (`core/intake/handling.infrastructure`); transactions via the `UnitOfWork` port. Remote calls (Policy system) happen **before** opening the transaction. Within a transaction save the claim before rows that reference it (documents FK).
- API: controllers only map HTTP ↔ commands/views. Claimant-visible data is decided in one place, `core.application.ClaimantViews` (never reserves, notes, handler ids, internal events). Errors: throw `DomainException(ErrorCode)`; `platform.api.ProblemDetailsAdvice` is the only handler, `ErrorStatuses` the exhaustive code→HTTP map. Claimants get 404 (`CLAIM_NOT_FOUND`) for others' claims via `ClaimantClaims.loadOwned`.
- Security: `platform.infrastructure.SecurityConfig` — `/claimant/**` CLAIMANT, `/staff/**` staff roles, else deny; `PUT /claimant/v1/uploads/*/content` is authorised by its HMAC signature (local S3 stand-in `LocalObjectStoreController`). Dev tokens: `POST /dev/token {"username":"priya|tan|arun|kumar|meera|lee"}` or a custom identity `{"sub","role","markets"}` (`dev` profile only). Without `OIDC_ISSUER_URI` or the dev secret the services refuse to start.
- Policy adapter `RestPolicyGateway`: JDK HttpClient pinned to HTTP/1.1 (h2c upgrade broke POSTs), 2 s timeouts, Caffeine 5-min cache, failures → `POLICY_SYSTEM_UNAVAILABLE` / `CoverCheck.Unavailable` (FNOL continues with cover PENDING).

- Staff surface: every state change goes through `core.application.StaffClaims.change(claimId, staff, expectedVersion, cmd)` — market scope (`FORBIDDEN_MARKET`, checked before the version so foreign claims reveal nothing) → If-Match version (`STALE_VERSION` 412) → domain command → save. Controllers parse `If-Match: "<version>"` with `platform.api.StaffRequest.expectedVersion` (missing → 428 `IF_MATCH_REQUIRED`) and return the new version as `ETag` in a `CommandResponse`. S9 answers 202 when the decision awaits a team lead, else 200.
- Authority limits live in `handling.authority_limit` (seeded by V4 for 6 markets × 2 LOB × HANDLER/TEAM_LEAD/MANAGER, cached 1 min by `JdbcAuthorityLimits`). Rejections are checked against the outstanding reserve. `ReviewDecision` checks self-approval before limits.
- Payment results: `settlement.application.RecordPaymentResult` (idempotent; stale instruction ids ignored). Fed by the signed webhook `POST /internal/v1/payments/callback` (`X-Signature: sha256=<HMAC of raw body>`, `claims.payments.webhook-secret`) and the `payments.payment-results` consumer (`PaymentResultsListener`).
- Messaging: `platform.infrastructure.OutboxRelay` publishes `platform.outbox` every 500 ms `ORDER BY seq`, single leader via `pg_try_advisory_xact_lock`, marks rows only after broker ack, stops at the first failure (ordering over availability). `settlement.infrastructure.PaymentResultsListener` consumes `payments.payment-results`. Background jobs (`RetryCoverChecks` 10 min, `ExpireInformationRequests` daily, `CloseCoolingOffClaims` daily) run through `JobLock` leases (`platform.job_lock`); disable with `claims.jobs.enabled=false`, relay with `claims.outbox.relay.enabled=false`. ITs drive jobs by calling `runOnce()` and the relay via `publishBatch()`.
- Bank account numbers: `platform.infrastructure.FieldCipher` (AES-256-GCM, `claims.crypto.field-key` / `FIELD_ENCRYPTION_KEY`, required; dev profile has a local key) encrypts `settlement.account_number_enc` with the settlement id as associated data. The relay masks `accountNumber` in `payments.settlement-requests` outbox rows once published.
- Rows written in one transaction share `created_at` (= transaction start): never order by it within a transaction. The outbox has an identity `seq`; the relay must publish `ORDER BY seq`.

## Domain model (claims-service)

- `core.domain.Claim` is the aggregate: state machine, `ReserveLedger`, information requests, assessment, `Decision`, `Settlement`. Commands take `(…, Actor actor, Instant now)`, check permissions → transition → record `ClaimEvent`s (`pullPendingEvents()` for the application layer to persist with claim_event + outbox).
- Transition table lives only in `ClaimStatus`. Commands whose target is reachable from several statuses (UNDER_ASSESSMENT: start assessment, info answered/expired, decision returned, appeal) must also call `requireSource(...)` — `ClaimCommandMatrixTest` (every command × every status) exists because the table alone let "appeal" run from ASSIGNED.
- Authority is computed outside the aggregate: `handling.domain.AuthorityPolicy` → `ApprovalRequirement` passed into `proposeDecision`; `requireCanApprove` before `approveDecision`. Self-approval is blocked in `Claim`.
- Error codes: `core.domain.ErrorCode` (stable API contract, never rename); `DomainException` carries one.
- Tests build claims with `ClaimFixture.inStatus(status)`, which drives real commands (no field poking) and clears events.

## Domain and integration contract

- Only Claims changes a claim. Lifecycle: SUBMITTED → TRIAGED → ASSIGNED → UNDER_ASSESSMENT ⇄ AWAITING_INFO → DECISION_PENDING → APPROVED → SETTLEMENT_IN_PROGRESS → SETTLED → CLOSED. Also: DECISION_PENDING → REJECTED → CLOSED; DECISION_PENDING → UNDER_ASSESSMENT (lead returns decision); REJECTED → UNDER_ASSESSMENT (appeal, ClaimReopened); SUBMITTED/TRIAGED/ASSIGNED/UNDER_ASSESSMENT/AWAITING_INFO → WITHDRAWN → CLOSED. Cover check failure/timeout leaves SUBMITTED with cover pending (retry job every 10 min). Auto-close job closes SETTLED/REJECTED/WITHDRAWN after cooling-off.
- Invalid transitions throw a domain exception; nothing sets status directly. Every change writes `claim_event` (CLAIMANT/INTERNAL visibility) + outbox row in the same transaction; relay (~500 ms) publishes. APIs never wait on Kafka.
- Topics (all keyed by claimId): `claims.claim-events` (12p/30d), `payments.settlement-requests` (6p/30d), `payments.payment-results` (6p/30d), `insights.alerts` (3p/7d), `<topic>.dlq` (14d, after 5 failures). Consumers are idempotent via `processed_event`.
- Money = `amountMinor` (long) + ISO currency. Reserves: append-only `reserve_movement`; released on settlement (ReserveChanged).
- Authority: limit per role/market/LOB. Within limit the decision is final immediately (S9 → DECISION_PENDING then APPROVED/REJECTED in one transaction); above it, 202 and a TEAM_LEAD approves or returns; proposer can't approve own.
- Policy system: sync REST, 2 s timeout, policies cached 5 min. Payments: Kafka (fallback if Payments can't use Kafka: REST instruction + signed webhook `POST /internal/v1/payments/callback`).
- Gateway routing (`gateway-service` `Routes`): Claims ← `/claimant/v1/{policies,uploads,claims}`, `/staff/v1/claims/**`; Insights ← `/staff/v1/{work-queue,search,approvals,insights/**}`; Notification ← `/claimant/v1/notifications*`, `/claimant/v1/notification-preferences`, `/staff/v1/claims/{id}/notifications`, `/admin/v1/templates`. Each service must only expose the paths routed to it.
- APIs: `/claimant/v1/**` vs `/staff/v1/**` with separate DTOs (never reserves/internal notes to claimants); RFC 9457 problem+json with stable `code`s in ONE global handler; Idempotency-Key on claim submission; If-Match/ETag (claim version) on staff state changes → 412; cursor pagination.
- Security: the gateway rejects missing/invalid tokens and the wrong role for a surface (`/claimant` CLAIMANT, `/staff` staff roles, `/admin` MANAGER); every service is still an OAuth2 resource server (JWT) and checks again, roles CLAIMANT, HANDLER, TEAM_LEAD, MANAGER; `markets` claim scopes staff queries; claimants get 404 for others' claims.
- Persistence: plain JDBC (`JdbcClient`) everywhere except notification-service reference data (preferences, templates, staff directory), which is Spring Data JPA in `notification.infrastructure.jpa` (entities map to domain records there; `ddl-auto: validate`, `open-in-view: false`). Either way Flyway owns the schema: every change is a new migration in the owning module's schema, and a JPA entity change needs a matching migration or startup fails validation.

## Project subagents (`.claude/agents/`)

`test-runner` (run/diagnose builds), `architecture-reviewer`, `api-reviewer`, `messaging-reviewer` (read-only reviews), `domain-modeler` (claim lifecycle changes + tests), `migration-author` (Flyway conventions). Subagents can't spawn other subagents; the main session orchestrates them.
