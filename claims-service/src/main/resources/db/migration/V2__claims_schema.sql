-- Claims data model. Tables are grouped by owning module (schema); the Claim aggregate repository writes the
-- claim and its children in one transaction. All money is *_minor BIGINT + currency CHAR(3).
-- Every table carries created_at/updated_at; market_code where relevant; claim.version for optimistic locking.

------------------------------------------------------------------------------------------------ platform
CREATE TABLE platform.market_config (
    market_code           CHAR(2)      PRIMARY KEY,
    name                  VARCHAR(60)  NOT NULL,
    currency              CHAR(3)      NOT NULL,
    timezone              VARCHAR(40)  NOT NULL,
    sla_hours             INT          NOT NULL CHECK (sla_hours > 0),
    info_request_due_days INT          NOT NULL CHECK (info_request_due_days > 0),
    cooling_off_days      INT          NOT NULL CHECK (cooling_off_days >= 0),
    languages             TEXT[]       NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Transactional outbox: written in the same transaction as the state change, published by the relay.
CREATE TABLE platform.outbox (
    id            UUID         PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    message_key   VARCHAR(100) NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    payload       JSONB        NOT NULL,
    headers       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    TEXT,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX outbox_unpublished ON platform.outbox (created_at) WHERE published_at IS NULL;

-- Idempotency-Key replay store, scoped per caller.
CREATE TABLE platform.idempotency_key (
    caller_id        VARCHAR(100) NOT NULL,
    idempotency_key  UUID         NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    response_status  INT          NOT NULL,
    response_body    JSONB        NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (caller_id, idempotency_key)
);

------------------------------------------------------------------------------------------------ core
CREATE SEQUENCE core.claim_number_seq START WITH 1;

CREATE TABLE core.claim (
    id                    UUID         PRIMARY KEY,
    claim_number          VARCHAR(20)  NOT NULL UNIQUE,
    market_code           CHAR(2)      NOT NULL REFERENCES platform.market_config,
    line_of_business      VARCHAR(10)  NOT NULL CHECK (line_of_business IN ('MOTOR', 'PROPERTY')),
    policy_number         VARCHAR(40)  NOT NULL,
    claimant_id           VARCHAR(100) NOT NULL,
    channel               VARCHAR(10)  NOT NULL CHECK (channel IN ('APP', 'STAFF')),
    currency              CHAR(3)      NOT NULL,
    incident_at           TIMESTAMPTZ  NOT NULL,
    claimed_amount_minor  BIGINT,
    status                VARCHAR(30)  NOT NULL,
    status_changed_at     TIMESTAMPTZ  NOT NULL,
    cover_status          VARCHAR(10)  NOT NULL CHECK (cover_status IN ('PENDING', 'CONFIRMED', 'DECLINED')),
    cover_decline_reason  VARCHAR(100),
    handler_id            VARCHAR(100),
    team_id               VARCHAR(100),
    sla_due_at            TIMESTAMPTZ  NOT NULL,
    sla_paused_at         TIMESTAMPTZ,
    approved_amount_minor BIGINT,
    submitted_at          TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX claim_by_claimant ON core.claim (claimant_id, submitted_at DESC, id DESC);
CREATE INDEX claim_by_market_status ON core.claim (market_code, status);
CREATE INDEX claim_by_handler ON core.claim (handler_id) WHERE handler_id IS NOT NULL;
CREATE INDEX claim_cover_pending ON core.claim (submitted_at) WHERE cover_status = 'PENDING';

-- Audit trail (INTERNAL) and customer timeline (CLAIMANT). Append-only.
CREATE TABLE core.claim_event (
    id           UUID         PRIMARY KEY,
    seq          BIGINT       GENERATED ALWAYS AS IDENTITY,
    claim_id     UUID         NOT NULL REFERENCES core.claim,
    market_code  CHAR(2)      NOT NULL,
    event_type   VARCHAR(60)  NOT NULL,
    visibility   VARCHAR(10)  NOT NULL CHECK (visibility IN ('CLAIMANT', 'INTERNAL')),
    actor_id     VARCHAR(100) NOT NULL,
    actor_role   VARCHAR(20)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    data         JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX claim_event_by_claim ON core.claim_event (claim_id, seq);

-- Upload metadata; the bytes live in document storage (filesystem/S3).
CREATE TABLE core.document (
    id            UUID         PRIMARY KEY,
    owner_id      VARCHAR(100) NOT NULL,
    claim_id      UUID         REFERENCES core.claim,
    market_code   CHAR(2),
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL CHECK (size_bytes > 0),
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_UPLOAD', 'UPLOADED')),
    storage_key   VARCHAR(255) NOT NULL,
    uploaded_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX document_by_claim ON core.document (claim_id);
CREATE INDEX document_by_owner ON core.document (owner_id);

------------------------------------------------------------------------------------------------ intake
-- Policy as it was when the claim was reported (the Policy system remains the source of truth).
CREATE TABLE intake.policy_snapshot (
    claim_id           UUID         PRIMARY KEY REFERENCES core.claim,
    market_code        CHAR(2)      NOT NULL,
    policy_number      VARCHAR(40)  NOT NULL,
    holder_id          VARCHAR(100) NOT NULL,
    holder_name        VARCHAR(200),
    line_of_business   VARCHAR(10)  NOT NULL,
    product            VARCHAR(100),
    policy_status      VARCHAR(20)  NOT NULL,
    coverage_start     DATE         NOT NULL,
    coverage_end       DATE         NOT NULL,
    currency           CHAR(3)      NOT NULL,
    sum_insured_minor  BIGINT,
    excess_minor       BIGINT,
    covered_perils     TEXT[]       NOT NULL,
    captured_at        TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- details: MOTOR or PROPERTY specific payload, validated per line of business before insert.
CREATE TABLE intake.incident (
    claim_id          UUID         PRIMARY KEY REFERENCES core.claim,
    market_code       CHAR(2)      NOT NULL,
    line_of_business  VARCHAR(10)  NOT NULL,
    peril             VARCHAR(30)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    location          TEXT,
    description       TEXT         NOT NULL,
    details           JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE intake.party (
    id                    UUID         PRIMARY KEY,
    claim_id              UUID         NOT NULL REFERENCES core.claim,
    market_code           CHAR(2)      NOT NULL,
    party_role            VARCHAR(20)  NOT NULL CHECK (party_role IN ('CLAIMANT', 'DRIVER', 'THIRD_PARTY', 'WITNESS')),
    name                  VARCHAR(200) NOT NULL,
    phone                 VARCHAR(40),
    email                 VARCHAR(200),
    vehicle_registration  VARCHAR(20),
    insurer               VARCHAR(100),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX party_by_claim ON intake.party (claim_id);

------------------------------------------------------------------------------------------------ handling
CREATE TABLE handling.info_request (
    id            UUID         PRIMARY KEY,
    claim_id      UUID         NOT NULL REFERENCES core.claim,
    market_code   CHAR(2)      NOT NULL,
    items         TEXT[]       NOT NULL,
    due_at        TIMESTAMPTZ  NOT NULL,
    requested_by  VARCHAR(100) NOT NULL,
    requested_at  TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'ANSWERED', 'EXPIRED', 'CANCELLED')),
    answer        TEXT,
    document_ids  UUID[]       NOT NULL DEFAULT '{}',
    closed_at     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX info_request_by_claim ON handling.info_request (claim_id, requested_at);
CREATE INDEX info_request_open_due ON handling.info_request (due_at) WHERE status = 'OPEN';

CREATE TABLE handling.assessment (
    id                     UUID         PRIMARY KEY,
    claim_id               UUID         NOT NULL REFERENCES core.claim,
    market_code            CHAR(2)      NOT NULL,
    liability              VARCHAR(10)  NOT NULL CHECK (liability IN ('FULL', 'PARTIAL', 'NONE')),
    assessed_amount_minor  BIGINT       NOT NULL CHECK (assessed_amount_minor >= 0),
    currency               CHAR(3)      NOT NULL,
    notes                  TEXT,
    assessed_by            VARCHAR(100) NOT NULL,
    assessed_at            TIMESTAMPTZ  NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX assessment_by_claim ON handling.assessment (claim_id, assessed_at);

-- Append-only reserve ledger: outstanding exposure = SUM(amount_minor) per claim.
CREATE TABLE handling.reserve_movement (
    id                   UUID         PRIMARY KEY,
    seq                  BIGINT       GENERATED ALWAYS AS IDENTITY,
    claim_id             UUID         NOT NULL REFERENCES core.claim,
    market_code          CHAR(2)      NOT NULL,
    line_of_business     VARCHAR(10)  NOT NULL,
    movement_type        VARCHAR(12)  NOT NULL CHECK (movement_type IN ('INITIAL', 'ADJUSTMENT', 'RELEASE')),
    amount_minor         BIGINT       NOT NULL CHECK (amount_minor <> 0),
    balance_after_minor  BIGINT       NOT NULL CHECK (balance_after_minor >= 0),
    currency             CHAR(3)      NOT NULL,
    reason               TEXT         NOT NULL,
    actor_id             VARCHAR(100) NOT NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX reserve_movement_by_claim ON handling.reserve_movement (claim_id, seq);
CREATE INDEX reserve_movement_exposure ON handling.reserve_movement (market_code, line_of_business, currency);

CREATE TABLE handling.decision (
    id                 UUID         PRIMARY KEY,
    claim_id           UUID         NOT NULL REFERENCES core.claim,
    market_code        CHAR(2)      NOT NULL,
    outcome            VARCHAR(10)  NOT NULL CHECK (outcome IN ('APPROVE', 'REJECT')),
    amount_minor       BIGINT       NOT NULL,
    currency           CHAR(3)      NOT NULL,
    reason_code        VARCHAR(60),
    proposed_by        VARCHAR(100) NOT NULL,
    proposed_at        TIMESTAMPTZ  NOT NULL,
    approval           VARCHAR(25)  NOT NULL CHECK (approval IN ('NONE', 'SUPERVISOR_APPROVAL')),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('AWAITING_APPROVAL', 'FINAL', 'RETURNED', 'SUPERSEDED')),
    decided_by         VARCHAR(100),
    decided_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX decision_by_claim ON handling.decision (claim_id, proposed_at);

CREATE TABLE handling.authority_limit (
    id                UUID         PRIMARY KEY,
    role              VARCHAR(20)  NOT NULL CHECK (role IN ('HANDLER', 'TEAM_LEAD', 'MANAGER')),
    market_code       CHAR(2)      NOT NULL REFERENCES platform.market_config,
    line_of_business  VARCHAR(10)  NOT NULL CHECK (line_of_business IN ('MOTOR', 'PROPERTY')),
    max_amount_minor  BIGINT       NOT NULL CHECK (max_amount_minor >= 0),
    currency          CHAR(3)      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (role, market_code, line_of_business)
);

------------------------------------------------------------------------------------------------ settlement
CREATE TABLE settlement.settlement (
    id               UUID         PRIMARY KEY,
    claim_id         UUID         NOT NULL UNIQUE REFERENCES core.claim,
    market_code      CHAR(2)      NOT NULL,
    amount_minor     BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)      NOT NULL,
    payee_name       VARCHAR(200) NOT NULL,
    bank_code        VARCHAR(20)  NOT NULL,
    account_number   VARCHAR(40)  NOT NULL,
    instruction_id   UUID         NOT NULL,
    attempts         INT          NOT NULL,
    status           VARCHAR(12)  NOT NULL CHECK (status IN ('INSTRUCTED', 'PAID', 'FAILED')),
    payment_ref      VARCHAR(60),
    failure_reason   VARCHAR(100),
    instructed_at    TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

------------------------------------------------------------------------------------------------ append-only guards
CREATE FUNCTION platform.reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_SCHEMA || '.' || TG_TABLE_NAME;
END
$$;

CREATE TRIGGER reserve_movement_append_only BEFORE UPDATE OR DELETE ON handling.reserve_movement
    FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();
CREATE TRIGGER claim_event_append_only BEFORE UPDATE OR DELETE ON core.claim_event
    FOR EACH ROW EXECUTE FUNCTION platform.reject_mutation();

COMMENT ON SCHEMA core IS 'claims-service core: claim aggregate root, claim_event audit/timeline, document metadata';
