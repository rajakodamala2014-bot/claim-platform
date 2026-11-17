-- TRIAGED is no longer a claim status. The queue is SUBMITTED and unassigned.
UPDATE insights.claim_summary SET status = 'SUBMITTED' WHERE status = 'TRIAGED';
