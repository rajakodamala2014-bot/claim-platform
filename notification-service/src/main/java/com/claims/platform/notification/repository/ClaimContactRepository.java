package com.claims.platform.notification.repository;

import com.claims.platform.notification.entity.ClaimContact;

import java.util.Optional;
import java.util.UUID;

/** The claim contact projection (who the customer, handler and team of a claim are). */
public interface ClaimContactRepository {
    Optional<ClaimContact> findByClaimId(UUID claimId);

    void save(ClaimContact contact);
}
