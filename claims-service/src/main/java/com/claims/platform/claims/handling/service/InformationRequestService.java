package com.claims.platform.claims.handling.service;

import com.claims.platform.claims.core.dto.response.ClaimantInformationRequestResponse;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.ClaimId;
import java.util.List;
import java.util.UUID;

/** Information requests from the customer's side (C7, C8) and their expiry job. */
public interface InformationRequestService {
    /** C7: what the company is asking the customer for (newest first). */
    List<ClaimantInformationRequestResponse> listInformationRequests(ClaimId claimId, String claimantId);

    /** C8: the customer answers an information request (text and/or documents). AWAITING_INFO -> UNDER_ASSESSMENT. */
    String respondToInformationRequest(ClaimId claimId, UUID requestId, Actor claimant, String answer, List<UUID> uploadIds);

    /** @return number of requests expired */
    int expireOverdueInformationRequests();
}
