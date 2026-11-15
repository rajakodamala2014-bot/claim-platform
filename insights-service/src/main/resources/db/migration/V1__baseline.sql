-- Baseline for insights-service. Read-model tables (work queue, workload, performance, exposure) and
-- processed_event arrive with the event consumers.
COMMENT ON SCHEMA insights IS 'insights-service: CQRS read models projected from claims.events';
