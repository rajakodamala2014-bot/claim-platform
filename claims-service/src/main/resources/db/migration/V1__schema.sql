CREATE TABLE market_config (
    market_code     CHAR(2) PRIMARY KEY,
    currency        CHAR(3) NOT NULL,
    sla_hours       INT NOT NULL,
    approval_limits JSONB NOT NULL,
    required_fields JSONB NOT NULL,
    languages       TEXT[] NOT NULL
);

CREATE SEQUENCE claim_number_seq START WITH 451;

CREATE TABLE claim (
    id                    UUID PRIMARY KEY,
    claim_number          VARCHAR(30) UNIQUE NOT NULL,
    market_code           CHAR(2) NOT NULL REFERENCES market_config,
    line_of_business      VARCHAR(10) NOT NULL CHECK (line_of_business IN ('MOTOR', 'PROPERTY')),
    policy_id             VARCHAR(40) NOT NULL,
    policy_number         VARCHAR(40) NOT NULL,
    claimant_id           VARCHAR(40) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    priority              VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    incident_date         TIMESTAMPTZ NOT NULL,
    incident_location     TEXT,
    description           TEXT,
    loss_type             VARCHAR(30),
    currency              CHAR(3) NOT NULL,
    estimated_amount      NUMERIC(14, 2),
    reserve_amount        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    paid_amount           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    coverage_decision     VARCHAR(20),
    liability_pct         SMALLINT,
    assigned_to           VARCHAR(40),
    team_id               VARCHAR(40),
    fraud_flag            BOOLEAN NOT NULL DEFAULT FALSE,
    sla_due_at            TIMESTAMPTZ,
    sla_paused_at         TIMESTAMPTZ,
    sla_paused_total_sec  BIGINT NOT NULL DEFAULT 0,
    reported_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at             TIMESTAMPTZ,
    version               INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_claim_queue ON claim (market_code, status, assigned_to, sla_due_at);
CREATE INDEX idx_claim_claimant ON claim (claimant_id);
CREATE INDEX idx_claim_assignee ON claim (assigned_to, status);

CREATE TABLE claim_motor_details (
    claim_id              UUID PRIMARY KEY REFERENCES claim (id),
    vehicle_registration  VARCHAR(20),
    make                  VARCHAR(40),
    model                 VARCHAR(40),
    driver_name           VARCHAR(100),
    third_party           JSONB,
    police_report_number  VARCHAR(40)
);

CREATE TABLE claim_property_details (
    claim_id          UUID PRIMARY KEY REFERENCES claim (id),
    property_address  TEXT,
    peril             VARCHAR(30),
    damaged_areas     TEXT[],
    habitable         BOOLEAN
);

CREATE TABLE claim_status_history (
    id                   BIGSERIAL PRIMARY KEY,
    claim_id             UUID NOT NULL REFERENCES claim (id),
    from_status          VARCHAR(20),
    to_status            VARCHAR(20) NOT NULL,
    actor_id             VARCHAR(40),
    reason               TEXT,
    visible_to_claimant  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_hist_claim ON claim_status_history (claim_id, created_at);

CREATE TABLE assessment (
    id                  UUID PRIMARY KEY,
    claim_id            UUID NOT NULL REFERENCES claim (id),
    assessor_id         VARCHAR(40) NOT NULL,
    coverage_decision   VARCHAR(20) NOT NULL,
    liability_pct       SMALLINT,
    findings            TEXT,
    estimated_loss      NUMERIC(14, 2),
    reserve             NUMERIC(14, 2),
    recommended_payout  NUMERIC(14, 2),
    created_at          TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE info_request (
    id                   UUID PRIMARY KEY,
    claim_id             UUID NOT NULL REFERENCES claim (id),
    requested_by         VARCHAR(40) NOT NULL,
    message              TEXT NOT NULL,
    requested_doc_types  TEXT[],
    status               VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    due_at               TIMESTAMPTZ NOT NULL,
    fulfilled_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE info_response (
    id               UUID PRIMARY KEY,
    info_request_id  UUID NOT NULL REFERENCES info_request (id),
    responder_id     VARCHAR(40) NOT NULL,
    message          TEXT,
    document_ids     UUID[],
    created_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE decision (
    id               UUID PRIMARY KEY,
    claim_id         UUID NOT NULL REFERENCES claim (id),
    decision         VARCHAR(10) NOT NULL CHECK (decision IN ('APPROVE', 'REJECT')),
    approved_amount  NUMERIC(14, 2),
    reason_code      VARCHAR(30),
    comment          TEXT,
    decided_by       VARCHAR(40) NOT NULL,
    decided_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE settlement (
    id                 UUID PRIMARY KEY,
    claim_id           UUID NOT NULL REFERENCES claim (id),
    amount             NUMERIC(14, 2) NOT NULL,
    currency           CHAR(3) NOT NULL,
    payee_name         VARCHAR(100),
    payee_type         VARCHAR(20),
    method             VARCHAR(20),
    bank_details_ref   VARCHAR(100),
    status             VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    payment_reference  VARCHAR(60),
    approved_by        VARCHAR(40),
    paid_at            TIMESTAMPTZ
);

CREATE TABLE claim_note (
    id          UUID PRIMARY KEY,
    claim_id    UUID NOT NULL REFERENCES claim (id),
    author_id   VARCHAR(40) NOT NULL,
    body        TEXT NOT NULL,
    internal    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    type          VARCHAR(50) NOT NULL,
    payload       JSONB NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_key (
    key           UUID PRIMARY KEY,
    request_hash  CHAR(64),
    response      JSONB,
    created_at    TIMESTAMPTZ DEFAULT now()
);
