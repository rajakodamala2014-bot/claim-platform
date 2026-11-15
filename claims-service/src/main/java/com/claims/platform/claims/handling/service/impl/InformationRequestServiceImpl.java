package com.claims.platform.claims.handling.service.impl;

import com.claims.platform.claims.core.dto.response.ClaimantInformationRequestResponse;
import com.claims.platform.claims.core.repository.ClaimRepository;
import com.claims.platform.claims.core.repository.ClaimWorkRepository.DueInformationRequest;
import com.claims.platform.claims.core.repository.ClaimWorkRepository;
import com.claims.platform.claims.core.mapper.ClaimantClaimMapper;
import com.claims.platform.claims.core.service.UnitOfWork;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Claim;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.core.service.ClaimantClaimAccessService;
import com.claims.platform.claims.core.service.DocumentService;
import com.claims.platform.claims.handling.service.InformationRequestService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InformationRequestServiceImpl implements InformationRequestService {
    private static final Logger log = LoggerFactory.getLogger(InformationRequestServiceImpl.class);
    private static final int BATCH = 500;

    private final ClaimantClaimAccessService claimAccess;
    private final ClaimRepository claims;
    private final DocumentService documentService;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final ClaimWorkRepository work;

    public InformationRequestServiceImpl(ClaimantClaimAccessService claimAccess, ClaimRepository claims,
                                         DocumentService documentService, UnitOfWork unitOfWork, Clock clock,
                                         ClaimWorkRepository work) {
        this.claimAccess = claimAccess;
        this.claims = claims;
        this.documentService = documentService;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
        this.work = work;
    }

    @Override
    public List<ClaimantInformationRequestResponse> listInformationRequests(ClaimId claimId, String claimantId) {
        return claimAccess.loadOwnedClaim(claimId, claimantId).informationRequests().reversed().stream()
                .map(ClaimantClaimMapper::toInformationRequestResponse)
                .toList();
    }

    @Override
    public String respondToInformationRequest(ClaimId claimId, UUID requestId, Actor claimant, String answer, List<UUID> uploadIds) {
        boolean noAnswer = answer == null || answer.isBlank();
        if (noAnswer && (uploadIds == null || uploadIds.isEmpty())) {
            throw ValidationException.of("answer", "send an answer or at least one document");
        }
        return unitOfWork.inTransaction(() -> {
            Claim claim = claimAccess.loadOwnedClaim(claimId, claimant.id());
            List<UUID> documentIds = documentService.attachDocumentsToClaim(uploadIds, claimId, claimant.id());
            claim.provideInformation(requestId, answer, documentIds, claimant, clock.instant());
            claims.save(claim);
            return claim.status().name();
        });
    }

    @Override
    public int expireOverdueInformationRequests() {
        Instant now = clock.instant();
        int expired = 0;
        for (DueInformationRequest due : work.findDueInformationRequests(now, BATCH)) {
            try {
                unitOfWork.inTransaction(() -> {
                    var claim = claims.findById(due.claimId()).orElseThrow();
                    claim.expireInformationRequest(due.requestId(), now);
                    claims.save(claim);
                    return null;
                });
                expired++;
            } catch (DomainException ex) {
                // answered or withdrawn between the query and now: nothing to do
                log.info("Skipped expiring request {} on claim {}: {}", due.requestId(), due.claimId(), ex.code());
            }
        }
        return expired;
    }
}
