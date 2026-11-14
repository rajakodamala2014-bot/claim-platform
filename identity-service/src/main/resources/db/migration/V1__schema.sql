CREATE TABLE app_user (
    id         VARCHAR(40) PRIMARY KEY,
    email      VARCHAR(150) UNIQUE NOT NULL,
    full_name  VARCHAR(120),
    phone      VARCHAR(30),
    role       VARCHAR(20) NOT NULL,
    status     VARCHAR(10) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE user_credential (
    user_id       VARCHAR(40) PRIMARY KEY REFERENCES app_user (id),
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE team (
    id          VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(80),
    market_code CHAR(2)
);

CREATE TABLE user_market (
    user_id     VARCHAR(40) REFERENCES app_user (id),
    market_code CHAR(2),
    PRIMARY KEY (user_id, market_code)
);

CREATE TABLE team_member (
    team_id VARCHAR(40) REFERENCES team (id),
    user_id VARCHAR(40) REFERENCES app_user (id),
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE policy_snapshot (
    policy_id        VARCHAR(40) PRIMARY KEY,
    policy_number    VARCHAR(40) UNIQUE NOT NULL,
    market_code      CHAR(2) NOT NULL,
    product          VARCHAR(10) NOT NULL,
    status           VARCHAR(12) NOT NULL,
    coverage_start   DATE,
    coverage_end     DATE,
    excess           NUMERIC(14, 2),
    cover_limit      NUMERIC(14, 2),
    currency         CHAR(3),
    covered_perils   TEXT[],
    insured_user_id  VARCHAR(40),
    fetched_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
