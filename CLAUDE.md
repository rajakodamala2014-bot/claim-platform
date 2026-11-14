# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Multi-module Maven project (Java 21, Spring Boot 3.4.5, Spring Cloud 2024.0.1) for an insurance claims platform. It handles motor and property claims across markets (SG, MY, HK, AU, GB, AE). Services are Spring MVC apps on virtual threads, each with its own PostgreSQL database and Flyway migrations.

## Build & test

No Maven wrapper is committed. Use the `mvn` on PATH.

**The root `pom.xml` lists modules that don't exist yet** (`document-service`, `notification-service`, `insights-service`, `api-gateway`), so a reactor build from the root fails until they're added or removed from `<modules>`. Until then, build each module on its own, in dependency order:

```sh
mvn -f claims-common/pom.xml install
mvn -f claims-web/pom.xml install
mvn -f identity-service/pom.xml package
mvn -f claims-service/pom.xml package
```

- Run a single test class or method: `mvn -f claims-common/pom.xml test -Dtest=JwtServiceTest` or `-Dtest=JwtServiceTest#methodName`
- Run a service: `mvn -f identity-service/pom.xml spring-boot:run`

No linter or formatter is configured.

## Runtime topology (from application.yml)

| Service | Port | DB |
|---|---|---|
| identity-service | 8081 | `jdbc:postgresql://localhost:5432/identity_db` |
| claims-service | 8082 | `jdbc:postgresql://localhost:5432/claims_db` |
| event consumers (not yet in repo) | 8084, 8085 | POST `/api/v1/internal/events` |

DB user/password is `claims`/`claims`. Hibernate runs with `ddl-auto: validate`, so every schema change needs a new Flyway migration (`src/main/resources/db/migration/V<n>__*.sql`), and entities must match it exactly. Swagger UI comes from springdoc.

## Module layering

- **claims-common**: plain Java, with no Spring dependency. It holds the contracts shared across services: the `ErrorBody`/`ErrorCodes` error envelope, `EventEnvelope`/`EventTypes`, `Money` (custom Jackson serializer always writes 2-decimal amounts and uppercases the currency), and `JwtService`/`AuthenticatedUser`.
- **claims-web**: shared Spring web infrastructure, pulled into each service by listing `com.claims.platform.web` in `@SpringBootApplication(scanBasePackages = ...)`. A new service must do the same.
- **identity-service**: users, teams, market assignments, policy snapshots. It issues JWTs (`POST /api/v1/auth/token`) and serves policies (`GET /api/v1/policies`) and users. `IdentityDataSeeder` seeds dev users on an empty DB, with password `Password123!` for `priya@example.com` (CLAIMANT), `arun@` (ADJUSTER), `meera@` (SENIOR_ADJUSTER) and `lee@` (MANAGER), plus policies `SG-123`, `SG-PROP-1` and `SG-EXPIRED`.
- **claims-service**: the claim lifecycle. So far it has only the domain, entities, repositories and schema. There are no services or controllers yet, and nothing reads the `claims.identity.base-url` or `claims.outbox.delay-ms` config.

## Cross-cutting conventions (claims-web)

- **Auth**: stateless JWT (HMAC). All services share `claims.security.jwt-secret`, which must be at least 32 bytes. `JwtAuthenticationFilter` puts an `AuthenticatedUser` principal with authority `ROLE_<role>` into the security context. Service code reads it through the `CurrentUser` helpers (`get()`, `requireStaff()`, `requireClaimant()`, `requireMarket(code)`), not through Spring annotations. Roles are CLAIMANT, ADJUSTER, SENIOR_ADJUSTER, MANAGER (staff = the last three), and SERVICE.
- **Public vs internal paths**: `claims.security.public-paths` are open to everyone. `claims.security.internal-paths` also accept an `X-Internal-Token` header (service-to-service), which authenticates as the `SERVICE` role. Health, info and swagger are always public.
- **Errors**: throw `ApiException` through its static factories (`notFound`, `conflict`, `unprocessable`, ...) with a code from `ErrorCodes`. `GlobalExceptionHandler` and the security handlers render everything as `{"error":{code,message,details,correlationId}}`.
- **Correlation IDs**: `CorrelationIdFilter` reads or creates `X-Correlation-Id`, puts it in MDC (it appears in log lines) and echoes it on the response. Outgoing calls should forward it.
- **Idempotency**: mutating endpoints wrap their work in `IdempotencyExecutor.execute(key, request, status, type, action)`, keyed by the `Idempotency-Key` UUID header. It hashes the request with SHA-256 and replays the stored response. If the same key comes with a different body, it returns 409 `IDEMPOTENCY_KEY_REUSED`. It needs an `idempotency_key` table in the service's DB, which only claims-service has.
- **Events**: transactional outbox. Rows go into `outbox_event` (claims-service), and a scheduled poller (`@EnableScheduling`; `OutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc`) is meant to publish them with `HttpEventPublisher`. That class POSTs an `EventEnvelope` to every `claims.events.consumer-urls` entry, with the internal token. The poller isn't written yet.

## Claims domain rules (claims-service `domain/`)

- **Status**: status values are string constants in `ClaimStatuses`, not an enum. Every status change must call `ClaimTransitions.require(from, to)`, which throws 422 `INVALID_STATE_TRANSITION` for a move the table doesn't allow. `CLOSED → IN_REVIEW` is the reopen path. Each status change should also write `claim_status_history`.
- **Market config**: `market_config` is seeded per market in `V2__seed_market_config.sql`. It sets the currency, SLA hours, per-role `approval_limits` (checked by `ApprovalAuthority.check`) and `required_fields` per line of business.
- **SLA**: `SlaClock.pause` and `SlaClock.resume` handle `AWAITING_INFO`. Resuming pushes `sla_due_at` back by the time spent paused.
- **Claim numbers**: `ClaimNumbers.format` builds `<MARKET>-<MTR|PRP>-<year>-<seq 6 digits>` from `claim_number_seq`.
- **Concurrency**: `ClaimRepository.lockById` takes a pessimistic write lock, and `claim.version` is there for optimistic `VERSION_MISMATCH` checks.
- **CHAR columns**: `market_code` and `currency` are `CHAR(n)` columns and can come back space-padded. Trim them (see `MarketConfig.market()`/`currencyCode()` and the `.trim()` calls in identity-service).

## Known issues

- `claims-service/.../repo/ClaimRepositories.java` declares `public interface MarketConfigRepository`. A public type must live in a file of the same name, so this file won't compile. Its other repositories are package-private, so code in other packages can't use them.
