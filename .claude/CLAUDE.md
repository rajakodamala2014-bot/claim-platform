# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Claims platform backend for MOTOR and PROPERTY claims across APAC markets (SG, HK, MY, TH, ID, AU — configurable). Java 21, Spring Boot 3.4, PostgreSQL 16, Flyway, Kafka (Redpanda locally), Maven multi-module mono-repo, plus a React customer and staff portal (`claims-ui`, npm).

Four deployable services: `gateway-service` (the public entry point) in front of `claims-service` (source of truth, the only writer of claims), `insights-service` (read models) and `notification-service`, which communicate only through Kafka. Everything else (identity provider, Policy system, Payments, file storage, e-mail/SMS/push) is an external system simulated locally. `README.md` covers building, running and the accounts; `deployment/README.md` covers deployment; Swagger UI on each service lists the endpoints (C1–C10, S1–S12, I1–I7, N1–N7); `http/happy-path.http`, `bruno/` and `deployment/smoke.sh` walk FNOL → settlement.

## Commands

```sh
./scripts/ci.sh                          # what CI runs: ./mvnw -B -ntp verify (unit + Testcontainers ITs)
./mvnw test                              # fast: unit/slice/embedded-Kafka tests (surefire, *Test)
./mvnw verify                            # also Testcontainers integration tests (failsafe, *IT)
./mvnw -pl claims-service -am test -Dtest=ClaimTest -Dsurefire.failIfNoSpecifiedTests=false   # one test class
./mvnw -pl claims-service -am verify -Dit.test=ClaimsServiceIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false  # one IT

docker compose -f deployment/docker-compose.yml up -d --build             # whole stack; gateway on http://localhost:8000
docker compose -f deployment/docker-compose.yml up -d postgres redpanda kafka-init redpanda-console policy-system payments-simulator   # infra only, run services from IDE
SPRING_PROFILES_ACTIVE=dev ./mvnw -pl claims-service -am spring-boot:run   # dev profile supplies the JWT secret + field key; without it startup fails
docker compose -f deployment/docker-compose.yml config -q                 # validate compose without a Docker daemon

cd claims-ui && npm install && npm run dev   # portal on :5173, proxies to the gateway on :8000
npm test                                 # vitest;  npx vitest run src/format.test.ts  for one file
npm run typecheck                        # tsc -b (there is no lint script);  npm run build = typecheck + vite build
```

Use `-pl <module> -am` (not `-f module/pom.xml`) so sibling SNAPSHOT modules resolve from the reactor. On Windows use `.\mvnw.cmd` from PowerShell (`$env:SPRING_PROFILES_ACTIVE='dev'`) or run the commands as written in Git Bash; `scripts/ci.sh` needs bash.

CI (`.github/workflows/ci.yml`) runs only `REQUIRE_DOCKER=1 ./scripts/ci.sh` on JDK 21 — `claims-ui` is not built or tested there, so run its `npm test` / `npm run build` yourself after UI changes.

Integration tests get PostgreSQL 16 from the `claims-common` test-jar's `TestPostgres.registerFreshDatabase(registry)` in a `@DynamicPropertySource`: Testcontainers when Docker is available (CI, `REQUIRE_DOCKER=1`), otherwise Zonky embedded Postgres 16 — so ITs **run** locally without Docker (`-Dtest.postgres=testcontainers` forbids the fallback). One server per JVM, fresh database per test class. ITs use `@ActiveProfiles("dev")` (dev JWT secret + upload signing secret). Kafka likewise: `TestKafka.register(registry, topics...)` (Testcontainers `apache/kafka` with Docker, in-JVM KRaft broker without) — it also sets a **unique `spring.kafka.consumer.group-id` per test context**, because cached Spring contexts sharing a group split partitions and steal each other's messages. So `@KafkaListener`s must not hard-code `groupId`; take it from `spring.kafka.consumer.group-id`. With several cached contexts alive, a message may be consumed (and dead-lettered) once per context — assert on content, not exact DLQ counts. HTTP stubs: WireMock standalone; `ClaimantJourneyIT` loads `deployment/docker/policy-system` mappings so the compose stub and tests share one contract.

## Local stack (deployment/docker-compose.yml)

| Name | Port (host) | What |
|---|---|---|
| gateway | 8000 | our `gateway-service` (Spring Cloud Gateway Server MVC): routes, validates JWTs, rate-limits, circuit-breaks, mints X-Correlation-Id, and proxies `/` to the `claims-ui` container (unrouted `/claimant`, `/staff`, `/admin`, `/internal`, `/actuator`, `/dev` paths are 404). Actuator on management port 8001 (not published). Services still validate JWTs themselves. |
| claims-service / insights-service / notification-service | 8080 / 8081 / 8082 | our services; DBs `claims` / `insights` / `notification` on postgres:5432 (user claims/claims) |
| payments-simulator | 8083 | fake Payments system: consumes `payments.settlement-requests`, answers on `payments.payment-results`. Account ending `0000` → FAILED. |
| policy-system | 8089 | WireMock (`deployment/docker/policy-system`) — **defines the Policy API contract**: `GET /api/v1/policies?holderId=`, `POST /api/v1/policies/{no}/cover-checks`. `SG-MTR-POL-1500` delays 3s to exercise the 2s timeout → cover pending. |
| redpanda / console | 19092 / 8090 | Kafka API; topics created by `kafka-init` from `deployment/docker/kafka/create-topics.sh` |
| sign-in | — | `POST /auth/login` on claims-service (`platform.app_user`), HS256 key `DEV_JWT_SECRET` shared by all services |

All Spring modules build from one `deployment/docker/service.Dockerfile` (`--build-arg SERVICE=<module>`). Document storage uses the filesystem implementation `LocalDocumentStorageServiceImpl` (volume `documents`); MinIO was dropped (no longer published on Docker Hub) — an S3 implementation can replace it behind `DocumentStorageService`.

## Modules

Six Maven modules (`claims-common`, the four services, `payments-simulator`) plus the npm project `claims-ui`. Do not add helper-only modules: there is deliberately no `claims-architecture` (ArchUnit) module and no `e2e-tests` module. `deployment/smoke.sh` checks the running stack end to end.

- `claims-common` — the one shared library.
  - `com.claims.platform.common.*`: contracts with no Spring/Jackson (`EventEnvelope`, `EventTypes`, `Topics` + `.dlq` convention, `SettlementRequest` / `PaymentResult` where `instructionId` is unique per attempt, `Money`, `Headers`). Entities may depend on these.
  - `com.claims.platform.web.{config,exception,common}`: auto-configured web plumbing (`CorrelationIdFilter`, `MaskingJsonLogFormatter` + `PiiMasker`, `PlatformSecurityAutoConfiguration` with the shared `JwtDecoder` and problem+json 401/403, `Problems`, `PlatformUser`, `PlatformOpenApiAutoConfiguration`).
  - `com.claims.platform.messaging.{config,consumer,exception}`: `ProcessedEvents.markProcessed(consumer, messageId)` (call in the same transaction as the side effects; table from `platform.messaging.processed-event-table`) and a `CommonErrorHandler` giving every listener 5 attempts then `<topic>.dlq`; throw `MalformedMessageException` to skip retries. Only active when both Kafka and JDBC are on the classpath.
  - Kafka, JDBC and springdoc are `optional` here: a service declares the ones it uses.
  - Its **test-jar** carries `com.claims.platform.testsupport.TestPostgres` / `TestKafka`; services depend on it with `<type>test-jar</type><scope>test</scope>` and declare the Testcontainers / Zonky / spring-kafka-test libraries themselves.
- `claims-service` — modular monolith: `core` (Claim aggregate + state machine), `intake`, `handling`, `settlement`, `platform`. One Flyway schema per module (`platform` holds the Flyway history).
- `insights-service` — read-only read models built from `claims.claim-events`; publishes `insights.alerts`. One `insights.claim_summary` row per claim, folded by `entity.ClaimSummary.apply(EventEnvelope)` (unknown events ignored) through `ClaimProjectionService`. `InsightsQueryServiceImpl` owns the access rules (markets, team, supervisor-only for I3–I7); `JdbcInsightsQueryRepository` holds the SQL. SLA runs until a decision and pauses in AWAITING_INFO; `SlaAlertScheduler` (5 min, advisory-lock leader) calls `SlaAlertService.publishDueSlaAlerts()`, which publishes SlaAtRisk / SlaBreached once per (claim, kind, due date) with deterministic event ids.
- `notification-service` — `NotificationEventConsumer` consumes `claims.claim-events` + `insights.alerts`. `NotificationPlanningServiceImpl` (in the consumer transaction) decides recipients via `common.NotificationRules` and renders per-language templates into PENDING `notification` rows; `NotificationDispatchScheduler` (1 s, `FOR UPDATE SKIP LOCKED`) sends them through `NotificationSender`s (fake providers) with 3 attempts + backoff, then FAILED. Languages `en, zh, zh-Hant (HK), ms, th, id`; template lookup falls back to the base language, then English. Keeps its own `claim_contact` projection (JDBC upsert) and seeded `staff_directory` / `preference` / `template` data (V3); never calls Claims. Alerts may arrive before claim events (different topics): staff notifications are built from the alert's own data.
- `gateway-service` — Spring Cloud Gateway **Server MVC** (servlet, so it reuses the web plumbing in `claims-common`; not the reactive flavor). One flat package, no layers:
  - `Routes` is the routing table (order = precedence; Notification and Insights paths before the Claims catch-alls). A new public path needs a route here, a rule in `GatewaySecurityConfig` if its surface/role is new, and a row in `RoutingTest`.
  - Filter order on API routes, outermost first: body limit (413) → `RateLimiter` (Bucket4j in memory, key = token `sub` else client IP) → `UpstreamBreakers` (Resilience4j, one per upstream; failure = exception or 502/503/504; no retries) → response headers → request headers → proxy. `EdgeHeaders.apiResponseHeaders` must stay directly around the proxy: gateway-built responses have read-only headers.
  - Gateway errors go through `GatewayProblems` (same problem+json shape as the services). Upstream URLs: `gateway.upstreams.*` (`CLAIMS_URL`, `INSIGHTS_URL`, `NOTIFICATION_URL`, `UI_URL`). `POST /auth/login` is routed to claims-service. Actuator is on management port 8001.
  - HTTP client is the JDK client pinned to HTTP/1.1 (same h2c problem as `RestPolicyClient`), which cannot set `Host`: upstreams get `X-Forwarded-Host/Proto/For` written by the gateway instead (caller-supplied values are dropped).
  - Tests extend `GatewayTestBase` (real gateway on a random port, four WireMock upstreams, rate limits effectively off); buckets and breakers live as long as the Spring context, so rate-limit tests use their own subjects and breakers are reset before each test.
- `payments-simulator` — local stand-in for the Payments system only; one flat package.
- `claims-ui` — customer and staff portal, React 18 + Vite + TypeScript (npm, **not** in the Maven reactor). Own compose service (`claims-ui/Dockerfile`: Node build → nginx with SPA fallback, `claims-ui/docker/nginx.conf`; host port 3000), reached through the gateway's catch-all route. Pages in `src/pages` (claims, new claim, claim detail, notifications, preferences for customers; work queue, staff claim, approvals, insights for staff); all calls go through `src/api.ts` to `/claimant/v1/**`, `/staff/v1/**`, `/admin/v1/**` and `POST /auth/login`, same origin (Vite dev server proxies to :8000). See `claims-ui/README.md`.

## Package layout (layered, interface + implementation)

Every service, and every module of claims-service, uses the same packages:

| Package | Holds |
|---|---|
| `controller` | REST controllers: map HTTP to service calls, nothing else. Annotated for OpenAPI |
| `service` / `service.impl` | Interface per service; `<Name>ServiceImpl` is a `@Service` with constructor injection |
| `repository` / `repository.impl` | Interface per repository; `Jdbc*` (or `Jpa*` in notification-service) implementation |
| `entity` | Business objects and rules: the `Claim` aggregate, value objects, read-model rows, JPA entities |
| `dto.request` / `dto.response` | One top-level record per request / response body, with `@Schema` |
| `mapper` | Static mappers between entities and DTOs |
| `producer` / `producer.impl` | Outbound messages (Kafka, outbox, e-mail / SMS / push senders) |
| `consumer` | `@KafkaListener`s |
| `client` / `client.impl` | Clients of external systems (Policy system) |
| `cache` / `cache.impl` | In-process Caffeine caches (policies, authority limits). There is no Redis |
| `scheduler` | `@Scheduled` triggers; the work itself is a service method |
| `config`, `exception`, `common` | Spring configuration and properties; exceptions and the one `GlobalExceptionHandler`; shared helpers |

Rules (convention, not build-enforced; use the `architecture-reviewer` subagent):

- Calls go controller / consumer / scheduler → service → repository. Controllers never touch a repository.
- Nothing outside an `impl` package (except `config`) references a class in an `impl` package: depend on the interface.
- `entity` code uses only the JDK, JPA / Hibernate mapping annotations, other entities, exceptions and `com.claims.platform.common..` — no Spring, Jackson, Kafka or JDBC.
- In claims-service, `intake`, `handling` and `settlement` must not depend on each other (share only `core`, `platform` and events).
- Method names say what the method does (`submitClaim`, `findPendingCoverChecks`, `toClaimResponse`), not `run` / `handle` / `execute`.

## claims-service conventions

- Persistence is plain JDBC (`JdbcClient`), no JPA: `core.repository.impl.JdbcClaimRepository` maps the aggregate (claim row with `version` check → `CONCURRENT_UPDATE`; children upserted; ledger rows insert-only; events → `core.claim_event` + `platform.outbox` in the same transaction through `OutboxClaimEventProducer`; `SettlementInstructed` also writes a `SettlementRequest` to `payments.settlement-requests`). pgjdbc needs `OffsetDateTime` (use `JdbcSupport.ts` / `instant`), arrays via `SqlArrayValue`. DB triggers make `reserve_movement` and `claim_event` append-only.
- Services: `core` (`ClaimantClaimAccessService`, `StaffClaimCommandService`, `DocumentService`, `DocumentStorageService`), `intake` (`ClaimIntakeService`, `ClaimQueryService`, `PolicyService`, `DocumentUploadService`, `CoverCheckRetryService`), `handling` (`ClaimHandlingService`, `InformationRequestService`, `ClaimClosureService`), `settlement` (`SettlementService`, `PaymentResultService`), `platform` (`AuthService`). Transactions go through `core.service.UnitOfWork` (not `@Transactional`). Remote calls (Policy system) happen **before** opening the transaction. Within a transaction save the claim before rows that reference it (documents FK).
- API: controllers only map HTTP ↔ service calls. Claimant-visible data is decided in one place, `core.mapper.ClaimantClaimMapper` (never reserves, notes, handler ids, internal events); staff views come from `StaffClaimMapper`. Errors: throw `DomainException(ErrorCode)`; `platform.exception.GlobalExceptionHandler` is the only handler, `ErrorCodeHttpStatusMapper` the exhaustive code→HTTP map. Claimants get 404 (`CLAIM_NOT_FOUND`) for others' claims via `ClaimantClaimAccessService.loadOwnedClaim`.
- OpenAPI: every endpoint has `@Operation` and an `@ApiResponse` per status it can return; error responses get their problem+json schema from `PlatformOpenApiAutoConfiguration`, so controllers state only code + description. Title and description come from `platform.openapi.*` in `application.yml`. New endpoints and DTOs must be documented the same way.
- Security: `platform.config.SecurityConfig` — `/claimant/**` CLAIMANT, `/staff/**` staff roles, else deny; `PUT /claimant/v1/uploads/*/content` (`DocumentStorageService.UPLOAD_CONTENT_PATH`) is authorised by its HMAC signature (local S3 stand-in `LocalObjectStoreController`). Sign-in: `POST /auth/login {"username","password"}` (`AuthController` → `AuthService`) against `platform.app_user` (V8; seeded users `priya`, `tan`, `wong`, `aisyah`, `arun`, `kumar`, `faiz`, `meera`, `ho`, `lee`; password `ChangeMe123!`). Without `OIDC_ISSUER_URI` or the dev secret the services refuse to start.
- Policy client `RestPolicyClient`: JDK HttpClient pinned to HTTP/1.1 (h2c upgrade broke POSTs), 2 s timeouts, 5-minute `CaffeinePolicyCache`, failures → `POLICY_SYSTEM_UNAVAILABLE` / `CoverCheck.Unavailable` (FNOL continues with cover PENDING).
- Staff surface: every state change goes through `StaffClaimCommandService.applyChange(claimId, staff, expectedVersion, command)` — market scope (`FORBIDDEN_MARKET`, checked before the version so foreign claims reveal nothing) → If-Match version (`STALE_VERSION` 412) → command on the aggregate → save. Controllers parse `If-Match: "<version>"` with `platform.common.StaffRequestSupport.parseExpectedVersion` (missing → 428 `IF_MATCH_REQUIRED`) and return the new version as `ETag` in a `CommandResponse` via `toResponseEntity`. S9 answers 202 when the decision awaits a team lead, else 200.
- Authority limits live in `handling.authority_limit` (seeded by V4 for 6 markets × 2 LOB × HANDLER/TEAM_LEAD/MANAGER; read by `JdbcAuthorityLimitRepository` through the 1-minute `CaffeineAuthorityLimitCache`). Rejections are checked against the outstanding reserve. `ClaimHandlingService.reviewDecision` checks self-approval before limits.
- Payment results: `PaymentResultService.recordPaymentResult` (idempotent; stale instruction ids ignored). Fed by `settlement.consumer.PaymentResultConsumer` (`payments.payment-results`) and by the signed webhook `POST /internal/v1/payments/callback` (`X-Signature: sha256=<HMAC of raw body>`, `claims.payments.webhook-secret`).
- Messaging: `platform.producer.impl.KafkaOutboxPublisher` publishes `platform.outbox` every 500 ms `ORDER BY seq`, single leader via `pg_try_advisory_xact_lock`, marks rows only after broker ack, stops at the first failure (ordering over availability). Background jobs (`CoverCheckRetryScheduler` 10 min; `HandlingJobsScheduler`: information-request expiry and cooling-off closure, daily) run through `JobLock` leases (`platform.job_lock`); disable with `claims.jobs.enabled=false`, the relay with `claims.outbox.relay.enabled=false`. ITs drive jobs by calling the service method (`retryPendingCoverChecks()`, `expireOverdueInformationRequests()`, `closeClaimsAfterCoolingOff()`) and the relay via `publishBatch()`.
- Bank account numbers: `platform.common.FieldCipher` (AES-256-GCM, `claims.crypto.field-key` / `FIELD_ENCRYPTION_KEY`, required; dev profile has a local key) encrypts `settlement.account_number_enc` with the settlement id as associated data. The relay masks `accountNumber` in `payments.settlement-requests` outbox rows once published.
- Rows written in one transaction share `created_at` (= transaction start): never order by it within a transaction. The outbox has an identity `seq`; the relay must publish `ORDER BY seq`.

## Claim model (claims-service)

- `core.entity.Claim` is the aggregate: state machine, `ReserveLedger`, information requests, assessment, `Decision`, `Settlement`. Its methods take `(…, Actor actor, Instant now)`, check permissions → transition → record `ClaimEvent`s (`pullPendingEvents()` lets the repository persist them with claim_event + outbox). Business rules stay in the aggregate; services only orchestrate.
- Transition table lives only in `ClaimStatus`. Commands whose target is reachable from several statuses (UNDER_ASSESSMENT: start assessment, info answered/expired, decision returned, appeal) must also call `requireSource(...)` — `ClaimCommandMatrixTest` (every command × every status) exists because the table alone let "appeal" run from ASSIGNED.
- Authority is computed outside the aggregate: `handling.entity.AuthorityPolicy` → `ApprovalRequirement` passed into `proposeDecision`; `requireCanApprove` before `approveDecision`. Self-approval is blocked in `Claim`.
- Error codes: `core.exception.ErrorCode` (stable API contract, never rename); `DomainException` carries one.
- Tests build claims with `ClaimFixture.inStatus(status)`, which drives real commands (no field poking) and clears events. Service tests use JUnit 5 + Mockito for collaborators, or the in-memory `InMemoryClaimRepository`.

## Business and integration contract

- Only Claims changes a claim. Lifecycle: SUBMITTED → TRIAGED → ASSIGNED → UNDER_ASSESSMENT ⇄ AWAITING_INFO → DECISION_PENDING → APPROVED → SETTLEMENT_IN_PROGRESS → SETTLED → CLOSED. Also: DECISION_PENDING → REJECTED → CLOSED; DECISION_PENDING → UNDER_ASSESSMENT (lead returns decision); REJECTED → UNDER_ASSESSMENT (appeal, ClaimReopened); SUBMITTED/TRIAGED/ASSIGNED/UNDER_ASSESSMENT/AWAITING_INFO → WITHDRAWN → CLOSED. Cover check failure/timeout leaves SUBMITTED with cover pending (retry job every 10 min). Auto-close job closes SETTLED/REJECTED/WITHDRAWN after cooling-off.
- Invalid transitions throw a `DomainException`; nothing sets status directly. Every change writes `claim_event` (CLAIMANT/INTERNAL visibility) + outbox row in the same transaction; relay (~500 ms) publishes. APIs never wait on Kafka.
- Topics (all keyed by claimId): `claims.claim-events` (12p/30d), `payments.settlement-requests` (6p/30d), `payments.payment-results` (6p/30d), `insights.alerts` (3p/7d), `<topic>.dlq` (14d, after 5 failures); created by `deployment/docker/kafka/create-topics.sh`. Consumers are idempotent via `processed_event`.
- Money = `amountMinor` (long) + ISO currency. Reserves: append-only `reserve_movement`; released on settlement (ReserveChanged).
- Authority: limit per role/market/LOB. Within limit the decision is final immediately (S9 → DECISION_PENDING then APPROVED/REJECTED in one transaction); above it, 202 and a TEAM_LEAD approves or returns; proposer can't approve own.
- Policy system: sync REST, 2 s timeout, policies cached 5 min. Payments: Kafka (fallback if Payments can't use Kafka: REST instruction + signed webhook `POST /internal/v1/payments/callback`).
- Gateway routing (`gateway-service` `Routes`): Claims ← `POST /auth/login`, `/claimant/v1/{policies,uploads,claims}`, `/staff/v1/claims/**`; Insights ← `/staff/v1/{work-queue,search,approvals,insights/**}`; Notification ← `/claimant/v1/notifications*`, `/claimant/v1/notification-preferences`, `/staff/v1/claims/{id}/notifications`, `/admin/v1/templates`, `/internal/v1/providers/**`. Each service must only expose the paths routed to it.
- APIs: `/claimant/v1/**` vs `/staff/v1/**` with separate DTOs (never reserves/internal notes to claimants); RFC 9457 problem+json with stable `code`s in ONE global handler per service; Idempotency-Key on claim submission; If-Match/ETag (claim version) on staff state changes → 412; cursor pagination.
- Security: the gateway rejects missing/invalid tokens and the wrong role for a surface (`/claimant` CLAIMANT, `/staff` staff roles, `/admin` MANAGER); every service is still an OAuth2 resource server (JWT) and checks again, roles CLAIMANT, HANDLER, TEAM_LEAD, MANAGER; `markets` claim scopes staff queries; claimants get 404 for others' claims.
- Persistence: plain JDBC (`JdbcClient`) everywhere except notification-service reference data (preferences, templates, staff directory), which is Spring Data JPA (`*EntityRepository` + `Jpa*Repository` in `notification.repository.impl`, JPA entities in `notification.entity`; `ddl-auto: validate`, `open-in-view: false`). Either way Flyway owns the schema: every change is a new migration in the owning module's schema, and a JPA entity change needs a matching migration or startup fails validation.

## Deployment

Everything deployment-related lives in `deployment/`: `docker-compose.yml` (local stack), `docker-compose.prod.yml` (secrets from `.env`, only the gateway published), `.env.example`, `docker/` (`service.Dockerfile`, Kafka topics, Policy system stub, Postgres init), `smoke.sh` (25-step end-to-end check) and `README.md` (the deployment guide). The compose build context is the repository root.

## Project subagents (`.claude/agents/`)

`test-runner` (run/diagnose builds), `architecture-reviewer`, `api-reviewer`, `messaging-reviewer` (read-only reviews), `domain-modeler` (claim lifecycle changes + tests), `migration-author` (Flyway conventions). Subagents can't spawn other subagents; the main session orchestrates them.
