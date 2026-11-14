package com.claims.platform.claims.repo;

import com.claims.platform.claims.domain.entity.Assessment;
import com.claims.platform.claims.domain.entity.ClaimDecision;
import com.claims.platform.claims.domain.entity.ClaimMotorDetails;
import com.claims.platform.claims.domain.entity.ClaimNote;
import com.claims.platform.claims.domain.entity.ClaimPropertyDetails;
import com.claims.platform.claims.domain.entity.ClaimStatusHistory;
import com.claims.platform.claims.domain.entity.InfoRequest;
import com.claims.platform.claims.domain.entity.InfoResponse;
import com.claims.platform.claims.domain.entity.MarketConfig;
import com.claims.platform.claims.domain.entity.OutboxEvent;
import com.claims.platform.claims.domain.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketConfigRepository extends JpaRepository<MarketConfig, String> {
}

interface MotorDetailsRepository extends JpaRepository<ClaimMotorDetails, UUID> {
}

interface PropertyDetailsRepository extends JpaRepository<ClaimPropertyDetails, UUID> {
}

interface ClaimHistoryRepository extends JpaRepository<ClaimStatusHistory, Long> {
    List<ClaimStatusHistory> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}

interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
}

interface InfoRequestRepository extends JpaRepository<InfoRequest, UUID> {
    List<InfoRequest> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    List<InfoRequest> findByClaimIdAndStatus(UUID claimId, String status);

    boolean existsByClaimIdAndStatus(UUID claimId, String status);
}

interface InfoResponseRepository extends JpaRepository<InfoResponse, UUID> {
}

interface DecisionRepository extends JpaRepository<ClaimDecision, UUID> {
    Optional<ClaimDecision> findFirstByClaimIdOrderByDecidedAtDesc(UUID claimId);
}

interface SettlementRepository extends JpaRepository<Settlement, UUID> {
}

interface ClaimNoteRepository extends JpaRepository<ClaimNote, UUID> {
    List<ClaimNote> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}

interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
