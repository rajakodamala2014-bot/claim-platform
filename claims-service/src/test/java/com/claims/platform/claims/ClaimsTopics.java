package com.claims.platform.claims;

import com.claims.platform.common.messaging.Topics;

/** Topics claims-service integration tests need on the test broker. */
public final class ClaimsTopics {
    public static final String[] ALL = {Topics.CLAIM_EVENTS, Topics.SETTLEMENT_REQUESTS, Topics.PAYMENT_RESULTS,
            Topics.dlq(Topics.PAYMENT_RESULTS)};

    private ClaimsTopics() {
    }
}
