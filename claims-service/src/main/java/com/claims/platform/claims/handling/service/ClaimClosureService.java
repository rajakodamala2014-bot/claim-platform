package com.claims.platform.claims.handling.service;



/** Auto-close job: closes SETTLED, REJECTED and WITHDRAWN claims after the market's cooling-off period. */
public interface ClaimClosureService {
    /** @return number of claims closed */
    int closeClaimsAfterCoolingOff();
}
