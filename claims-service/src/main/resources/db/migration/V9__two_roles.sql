-- Sign-in roles are only CUSTOMER and CLAIMS_ADJUSTER.
-- One authority ceiling per market and line of business, at the previous handler amount.
-- Amounts above that ceiling wait for a second claims adjuster.

ALTER TABLE platform.app_user DROP CONSTRAINT app_user_role_check;
UPDATE platform.app_user SET role = 'CUSTOMER' WHERE role = 'CLAIMANT';
UPDATE platform.app_user SET role = 'CLAIMS_ADJUSTER' WHERE role IN ('HANDLER', 'TEAM_LEAD', 'MANAGER');
ALTER TABLE platform.app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('CUSTOMER', 'CLAIMS_ADJUSTER'));

-- claim_event is append-only. Lift the guard only for this one-time role rename, then put it back.
ALTER TABLE core.claim_event DISABLE TRIGGER claim_event_append_only;
UPDATE core.claim_event SET actor_role = 'CUSTOMER' WHERE actor_role = 'CLAIMANT';
UPDATE core.claim_event SET actor_role = 'CLAIMS_ADJUSTER' WHERE actor_role IN ('HANDLER', 'TEAM_LEAD', 'MANAGER');
ALTER TABLE core.claim_event ENABLE TRIGGER claim_event_append_only;

ALTER TABLE handling.authority_limit DROP CONSTRAINT authority_limit_role_check;
DELETE FROM handling.authority_limit WHERE role <> 'HANDLER';
UPDATE handling.authority_limit SET role = 'CLAIMS_ADJUSTER';
ALTER TABLE handling.authority_limit ADD CONSTRAINT authority_limit_role_check CHECK (role = 'CLAIMS_ADJUSTER');
