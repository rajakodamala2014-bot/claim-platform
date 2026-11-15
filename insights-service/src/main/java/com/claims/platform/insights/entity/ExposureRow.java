package com.claims.platform.insights.entity;

/** Open claims and total reserve of one market x line of business x currency. */
public record ExposureRow(String market, String lineOfBusiness, String currency, int outstandingClaims,
                          long totalReserveMinor) {
}
