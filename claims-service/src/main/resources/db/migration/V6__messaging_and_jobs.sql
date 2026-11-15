-- Consumer idempotency (payments.payment-results) and leases for background jobs across instances.
CREATE TABLE platform.processed_event (
    consumer      VARCHAR(100) NOT NULL,
    message_id    VARCHAR(200) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);

-- One row per job; an instance runs the job only while it holds an unexpired lease.
CREATE TABLE platform.job_lock (
    name          VARCHAR(60)  PRIMARY KEY,
    locked_until  TIMESTAMPTZ  NOT NULL,
    locked_by     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
