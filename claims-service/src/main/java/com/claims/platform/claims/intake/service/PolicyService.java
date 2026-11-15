package com.claims.platform.claims.intake.service;

import com.claims.platform.claims.intake.entity.PolicySnapshot;
import java.util.List;

/** The customer's policies, from the Policy system. */
public interface PolicyService {
    /** C1: the customer's active policies for the "Report an incident" form. */
    List<PolicySnapshot> listPoliciesOfHolder(String claimantId);
}
