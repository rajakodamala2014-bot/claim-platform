-- Rows written in one transaction share created_at (now() is the transaction start), so created_at can't order
-- them. The relay publishes by seq, which preserves the order events were recorded in (per-claim ordering on
-- Kafka relies on it).
ALTER TABLE platform.outbox ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;
DROP INDEX platform.outbox_unpublished;
CREATE INDEX outbox_unpublished ON platform.outbox (seq) WHERE published_at IS NULL;
