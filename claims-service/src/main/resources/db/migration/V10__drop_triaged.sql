-- TRIAGED is no longer a claim status. Existing rows stay submitted and assignable.
UPDATE core.claim SET status = 'SUBMITTED' WHERE status = 'TRIAGED';
