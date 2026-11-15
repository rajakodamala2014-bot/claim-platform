package com.claims.platform.claims.core.entity;

/**
 * Outcome of the authority check (computed by handling's AuthorityPolicy, applied by the Claim).
 */
public enum ApprovalRequirement {
    /** Within the proposer's limit: the decision is final straight away. */
    NONE,
    /** Above the proposer's limit: a team lead (or manager) other than the proposer must approve. */
    SUPERVISOR_APPROVAL
}
