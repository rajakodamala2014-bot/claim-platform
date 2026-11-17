package com.claims.platform.claims.intake.service;



/** Cover-check retry job for claims saved while the Policy system was unreachable. */
public interface CoverCheckRetryService {
    record CoverCheckRetryResult(int triaged, int declined, int stillPending) {
    }

    /**
     * Cover-check retry job (every 10 min): claims saved while the Policy system was unreachable get their cover
     * check now and confirms cover. The claim stays SUBMITTED. If the Policy system
     * is still down the run stops early and the claims wait for the next run. Remote calls happen outside
     * transactions; each claim is re-read and re-checked inside its own transaction before it is changed.
     */
    CoverCheckRetryResult retryPendingCoverChecks();
}
