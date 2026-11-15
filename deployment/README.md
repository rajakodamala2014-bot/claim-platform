# Deploying the claims platform

Everything needed to deploy lives in this `deployment/` folder: the Compose files, the production override, `.env.example`, the image recipe and infrastructure setup under `docker/`, and the smoke test. Everything (backend, UI, infrastructure) is packaged with Docker Compose. One command builds the latest code
of both the backend and the UI and starts it.

## 1. What gets deployed

| Container | Built from | Port (inside) | Role |
|---|---|---|---|
| `gateway` | `gateway-service` | 8000 | The only public entry point: routes API calls, validates tokens, rate-limits, serves the UI through `claims-ui` |
| `claims-ui` | `claims-ui/` (React + Vite, served by nginx) | 80 | Customer and staff portal |
| `claims-service` | `claims-service` | 8080 | Claims, sign-in (`POST /auth/login`), document uploads |
| `insights-service` | `insights-service` | 8081 | Work queues, search, approvals, dashboards |
| `notification-service` | `notification-service` | 8082 | Inbox, preferences, templates, e-mail / SMS / push |
| `payments-simulator` | `payments-simulator` | 8083 | Stand-in for the Payments system (replace with the real one) |
| `policy-system` | WireMock image + `docker/policy-system` | 8080 | Stand-in for the Policy system (replace with the real one) |
| `postgres` | `postgres:16-alpine` | 5432 | Databases `claims`, `insights`, `notification` |
| `redpanda` + `kafka-init` | Redpanda image + `docker/kafka/create-topics.sh` | 9092 | Kafka API; `kafka-init` creates the topics and exits |

Users only ever talk to the gateway. Database schemas are created and upgraded automatically by Flyway when
each service starts; there is no manual migration step.

## 2. Prerequisites

* Docker Engine 24+ with the Compose plugin v2.24+ (the production override uses `!reset` / `!override`).
* About 6 GB of free memory for the full stack and about 5 GB of disk for images.
* Nothing else is needed on the server: Java and Node are only used inside the image builds.

To build and test outside Docker (optional): JDK 21, Node 22, and `./mvnw` (Maven wrapper, included).

## 3. Run it locally (developer machine)

```sh
git pull                                 # latest code
cd deployment
docker compose up -d --build             # builds backend + UI images and starts everything
./smoke.sh                               # 25 checks: sign-in -> claim -> approval -> payment -> notifications
```

Open http://localhost:8000 and sign in. Every seeded account has the password `ChangeMe123!`:

| Username | Role | Market |
|---|---|---|
| `priya`, `tan` | Customer | SG |
| `wong` / `aisyah` | Customer | HK / MY |
| `arun`, `kumar` | Claims handler | SG |
| `meera` | Team lead | SG |
| `lee` | Manager | all markets |

Other local URLs: Swagger UI per service at http://localhost:8080/swagger-ui.html (claims), `:8081` (insights),
`:8082` (notification); Kafka console at http://localhost:8090.

Stop with `docker compose down` (add `-v` to also delete the database, Kafka data and uploaded documents).

## 4. Deploy to a server

```sh
git clone <repository> claim-platform && cd claim-platform     # or: git pull
cd deployment
cp .env.example .env                                           # fill in every value (see below)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
GATEWAY=http://localhost:8000 PASSWORD='ChangeMe123!' ./smoke.sh
```

`docker-compose.prod.yml` changes three things compared with the local stack: all secrets come from `.env`,
only the gateway is published on the host, and containers restart automatically.

### Values in `.env`

| Variable | What it is | How to create it |
|---|---|---|
| `PUBLIC_BASE_URL` | The URL users open, e.g. `https://claims.example.com`. Upload links are built from it | your domain |
| `GATEWAY_PORT` | Host port of the gateway (default 8000) | |
| `DB_USER`, `DB_PASSWORD` | PostgreSQL owner of the three databases. Applied only when the database volume is first created | choose |
| `JWT_SECRET` | Signs login tokens; shared by gateway and services. 32+ characters | `openssl rand -hex 32` |
| `DOCUMENT_SIGNING_SECRET` | Signs upload URLs. 32+ characters | `openssl rand -hex 32` |
| `PAYMENTS_WEBHOOK_SECRET` | Verifies the payments fallback webhook. 32+ characters | `openssl rand -hex 32` |
| `FIELD_ENCRYPTION_KEY` | Encrypts bank account numbers at rest. Never change or lose it once data exists | `openssl rand -base64 32` |
| `PROVIDER_CALLBACK_SECRET` | Verifies delivery receipts from e-mail / SMS providers. 32+ characters | `openssl rand -hex 32` |

### Before real users

1. **TLS:** put a load balancer or reverse proxy that terminates HTTPS in front of the gateway port and
   forward to it. The gateway itself speaks plain HTTP.
2. **Passwords:** the seeded accounts all use `ChangeMe123!`. Change them in `platform.app_user` (bcrypt hashes) in
   the `claims` database, or switch to an identity provider (below).
3. **External systems:** point `POLICY_BASE_URL` (claims-service) at the real Policy system and remove
   `policy-system`; remove `payments-simulator` once the real Payments system consumes
   `payments.settlement-requests` and answers on `payments.payment-results`.
4. **Managed infrastructure (optional):** to use an existing PostgreSQL or Kafka, set `DB_URL` / `DB_USER` /
   `DB_PASSWORD` and `KAFKA_BOOTSTRAP` on the services and remove `postgres` / `redpanda` / `kafka-init`.
   Create the databases `claims`, `insights`, `notification` and the topics in `docker/kafka/create-topics.sh` first.
5. **Backups:** the volumes `postgres-data` (all business data) and `documents` (uploaded files) must be backed up.

### Using an identity provider instead of the built-in sign-in

Set `OIDC_ISSUER_URI` on `gateway`, `claims-service`, `insights-service` and `notification-service`. Tokens
are then validated against the provider's JWKS and must carry the claims `sub`, `roles`
(`CLAIMANT` / `HANDLER` / `TEAM_LEAD` / `MANAGER`), `markets`, and for staff `team`. The UI's sign-in page uses
`POST /auth/login`, so with an external provider the UI needs that provider's login flow added.

## 5. Updating to the latest code

```sh
git pull
cd deployment
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
./smoke.sh
```

Compose rebuilds only the images whose sources changed and restarts only those containers. Data is kept.
To rebuild just the UI: `docker compose ... up -d --build claims-ui`. A service: `... up -d --build claims-service`.

## 6. Checking and troubleshooting

```sh
cd deployment
docker compose ps                                   # everything should be "Up"; kafka-init "Exited (0)"
docker compose logs -f gateway claims-service       # JSON logs, personal data masked
docker compose exec gateway curl -s localhost:8001/actuator/health     # gateway health (management port)
docker compose exec claims-service curl -s localhost:8080/actuator/health
```

| Symptom | Likely cause |
|---|---|
| A service exits at start with a message about a secret or key | A value in `.env` is missing or shorter than 32 characters |
| `401 UNAUTHENTICATED` on every call after a redeploy | `JWT_SECRET` changed: sign in again |
| `503 UPSTREAM_UNAVAILABLE` from the gateway | That service is down or still starting; the gateway retries it after 10 s |
| `429 RATE_LIMITED` | Per-client limit reached; raise `RATE_LIMIT_*` in `.env` |
| Claim stays `SETTLEMENT_IN_PROGRESS` | Nothing is answering on `payments.payment-results` (payments-simulator / Payments system down) |
| Claim submitted with cover `PENDING` | Policy system unreachable; it is retried every 10 minutes |
| Upload link fails | `PUBLIC_BASE_URL` does not match the address the browser uses |

## 7. Building and testing without Docker

```sh
# from the repository root
./mvnw clean verify                      # backend: unit + integration tests (embedded PostgreSQL / Kafka when Docker is absent)
cd claims-ui && npm ci && npm test && npm run build     # UI: tests + production bundle in claims-ui/dist
```

Run a single service from source against the compose infrastructure:

```sh
docker compose -f deployment/docker-compose.yml up -d postgres redpanda kafka-init policy-system payments-simulator
SPRING_PROFILES_ACTIVE=dev ./mvnw -pl claims-service -am spring-boot:run
cd claims-ui && npm run dev              # UI with hot reload on http://localhost:5173, proxying to the gateway on :8000
```
