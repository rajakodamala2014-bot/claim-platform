-- What Notifications needs, kept locally so it never calls Claims: a small claim projection, the staff
-- directory (names, e-mails, team leads), customer preferences, templates, and the delivery log (= in-app inbox).

CREATE TABLE notification.claim_contact (
    claim_id          UUID         PRIMARY KEY,
    claim_number      VARCHAR(20)  NOT NULL,
    claimant_id       VARCHAR(100) NOT NULL,
    market_code       CHAR(2)      NOT NULL,
    line_of_business  VARCHAR(10)  NOT NULL,
    handler_id        VARCHAR(100),
    team_id           VARCHAR(100),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification.staff_directory (
    user_id     VARCHAR(100) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(200) NOT NULL,
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('HANDLER', 'TEAM_LEAD', 'MANAGER')),
    team_id     VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notification.preference (
    user_id        VARCHAR(100) PRIMARY KEY,
    language       VARCHAR(10)  NOT NULL DEFAULT 'en',
    email          VARCHAR(200),
    email_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    phone          VARCHAR(40),
    sms_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    push_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- channel NULL = any channel; subject is the e-mail subject and the in-app title.
CREATE TABLE notification.template (
    id          VARCHAR(80)  PRIMARY KEY,
    event_type  VARCHAR(60)  NOT NULL,
    audience    VARCHAR(12)  NOT NULL CHECK (audience IN ('CUSTOMER', 'HANDLER', 'TEAM_LEAD')),
    channel     VARCHAR(10)  CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    language    VARCHAR(10)  NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX template_lookup ON notification.template (event_type, audience, COALESCE(channel, '*'), language);

CREATE TABLE notification.notification (
    id                   UUID         PRIMARY KEY,
    recipient_id         VARCHAR(100) NOT NULL,
    audience             VARCHAR(12)  NOT NULL,
    claim_id             UUID,
    claim_number         VARCHAR(20),
    event_id             UUID         NOT NULL,
    event_type           VARCHAR(60)  NOT NULL,
    channel              VARCHAR(10)  NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),
    address              VARCHAR(200),
    language             VARCHAR(10)  NOT NULL,
    subject              VARCHAR(200) NOT NULL,
    body                 TEXT         NOT NULL,
    status               VARCHAR(12)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'BOUNCED', 'FAILED')),
    attempts             INT          NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error           TEXT,
    provider             VARCHAR(30),
    provider_message_id  VARCHAR(100),
    sent_at              TIMESTAMPTZ,
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (event_id, recipient_id, channel)
);
CREATE INDEX notification_inbox ON notification.notification (recipient_id, created_at DESC) WHERE channel = 'IN_APP';
CREATE INDEX notification_by_claim ON notification.notification (claim_id, created_at);
CREATE INDEX notification_due ON notification.notification (next_attempt_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX notification_provider_message ON notification.notification (provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;

CREATE TABLE notification.processed_event (
    consumer      VARCHAR(100) NOT NULL,
    message_id    VARCHAR(200) NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, message_id)
);
