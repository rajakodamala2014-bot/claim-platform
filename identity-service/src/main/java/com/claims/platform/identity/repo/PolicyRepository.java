package com.claims.platform.identity.repo;

import com.claims.platform.identity.domain.PolicySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<PolicySnapshot, String> {
    Optional<PolicySnapshot> findByPolicyNumber(String policyNumber);

    List<PolicySnapshot> findByInsuredUserId(String insuredUserId);
}
