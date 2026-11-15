-- CQRS read model projected from claims.claim-events. One row per claim, denormalised for the staff screens
-- (work queue, search, approvals inbox, workload, performance, SLA, exposure). Rebuildable by replaying events.
CREATE TABLE insights.claim_summary (
    claim_id                      UUID         PRIMARY KEY,
    claim_number                  VARCHAR(20)  NOT NULL,
    market_code                   CHAR(2)      NOT NULL,
    line_of_business              VARCHAR(10)  NOT NULL,
    policy_number                 VARCHAR(40),
    claimant_id                   VARCHAR(100),
    channel                       VARCHAR(10),
    status                        VARCHAR(30)  NOT NULL,
    cover_status                  VARCHAR(10)  NOT NULL,
    handler_id                    VARCHAR(100),
    team_id                       VARCHAR(100),
    submitted_at                  TIMESTAMPTZ  NOT NULL,
    sla_due_at                    TIMESTAMPTZ  NOT NULL,
    sla_paused                    BOOLEAN      NOT NULL DEFAULT FALSE,
    decided_at                    TIMESTAMPTZ,
    settled_at                    TIMESTAMPTZ,
    closed_at                     TIMESTAMPTZ,
    reserve_outstanding_minor     BIGINT       NOT NULL DEFAULT 0,
    currency                      CHAR(3)      NOT NULL,
    pending_decision_id           UUID,
    pending_decision_outcome      VARCHAR(10),
    pending_decision_amount_minor BIGINT,
    pending_decision_proposed_by  VARCHAR(100),
    pending_decision_proposed_at  TIMESTAMPTZ,
    reopen_count                  INT          NOT NULL DEFAULT 0,
    last_event_at                 TIMESTAMPTZ  NOT NULL,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX claim_summary_queue ON insights.claim_summary (market_code, status, sla_due_at, claim_id);
CREATE INDEX claim_summary_handler ON insights.claim_summary (handler_id, status);
CREATE INDEX claim_summary_team ON insights.claim_summary (team_id, status);
CREATE INDEX claim_summary_pending ON insights.claim_summary (market_code) WHERE pending_decision_id IS NOT NULL;
CREATE INDEX claim_summary_closed ON insights.claim_summary (closed_at) WHERE closed_at IS NOT NULL;

CREATE TABLE insights.processed_event (
    consumer      VARCHAR(100) NOT NULL,
    message_id    VARCHAR(200) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);

-- SLA alerts already published, per due date (a resumed SLA clock moves the due date and may alert again).
CREATE TABLE insights.sla_alert (
    claim_id    UUID         NOT NULL,
    kind        VARCHAR(12)  NOT NULL CHECK (kind IN ('AT_RISK', 'BREACHED')),
    sla_due_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (claim_id, kind, sla_due_at)
);
