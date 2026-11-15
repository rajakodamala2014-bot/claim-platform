-- Baseline: one schema per module (Flyway creates them from spring.flyway.schemas).
-- Ownership is recorded on the schema so it is visible to anyone inspecting the database.
COMMENT ON SCHEMA platform IS 'claims-service platform module: outbox, idempotency keys, market config';
COMMENT ON SCHEMA core IS 'claims-service core: claim aggregate and claim_event audit/timeline';
COMMENT ON SCHEMA intake IS 'claims-service intake module: party, policy_snapshot, incident, document';
COMMENT ON SCHEMA handling IS 'claims-service handling module: assessment, info_request, reserve_movement, decision, authority_limit';
COMMENT ON SCHEMA settlement IS 'claims-service settlement module: settlement';
