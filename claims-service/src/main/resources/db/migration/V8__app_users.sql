-- Accounts that sign in to the platform. The access token is issued from this row;
-- services still check the token's role and markets on every call.
CREATE TABLE platform.app_user (
    user_id       VARCHAR(100) PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CLAIMANT', 'HANDLER', 'TEAM_LEAD', 'MANAGER')),
    markets       VARCHAR(8)[] NOT NULL,
    team_id       VARCHAR(100),
    locale        VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed password for every account is ChangeMe123! (bcrypt). Rotate these before a real deployment.
INSERT INTO platform.app_user (user_id, username, password_hash, display_name, role, markets, team_id, locale) VALUES
    ('cust-priya',  'priya',  '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Priya Nair',              'CLAIMANT',  ARRAY['SG'],                          NULL,             'en-SG'),
    ('cust-tan',    'tan',    '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Tan Wei Ling',            'CLAIMANT',  ARRAY['SG'],                          NULL,             'zh-SG'),
    ('cust-wong',   'wong',   '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Wong Siu Ming',           'CLAIMANT',  ARRAY['HK'],                          NULL,             'zh-HK'),
    ('cust-aisyah', 'aisyah', '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Nur Aisyah Binti Ahmad',  'CLAIMANT',  ARRAY['MY'],                          NULL,             'ms-MY'),
    ('h-arun',      'arun',   '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Arun Kumar',              'HANDLER',   ARRAY['SG'],                          'team-sg-motor',  'en-SG'),
    ('h-kumar',     'kumar',  '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Kumar Raj',               'HANDLER',   ARRAY['SG'],                          'team-sg-motor',  'en-SG'),
    ('h-my-faiz',   'faiz',   '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Faiz Rahman',             'HANDLER',   ARRAY['MY'],                          'team-my',        'ms-MY'),
    ('tl-meera',    'meera',  '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Meera Shah',              'TEAM_LEAD', ARRAY['SG', 'MY'],                    'team-sg-motor',  'en-SG'),
    ('tl-hk-ho',    'ho',     '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Ho Wai Lam',              'TEAM_LEAD', ARRAY['HK'],                          'team-hk',        'en-HK'),
    ('m-lee',       'lee',    '$2a$10$0dpVNERtq3yK2ti5iwF2qud7/xVpyJQKKWDKVlJGybTnrAl3iGT16', 'Lee Chen',                'MANAGER',   ARRAY['SG', 'HK', 'MY', 'TH', 'ID', 'AU'], NULL,             'en-SG');
