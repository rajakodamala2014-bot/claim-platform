package com.claims.platform.insights.mapper;

import com.claims.platform.common.money.Money;
import com.claims.platform.insights.dto.response.ApprovalItemResponse;
import com.claims.platform.insights.dto.response.ClaimItemResponse;
import com.claims.platform.insights.dto.response.ExposureItemResponse;
import com.claims.platform.insights.dto.response.MoneyResponse;
import com.claims.platform.insights.dto.response.PerformanceResponse;
import com.claims.platform.insights.dto.response.WorkloadResponse;
import com.claims.platform.insights.entity.ApprovalRow;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ExposureRow;
import com.claims.platform.insights.entity.PerformanceRow;
import com.claims.platform.insights.entity.SlaState;
import com.claims.platform.insights.entity.WorkloadRow;

import java.time.Duration;
import java.time.Instant;

/** Maps read-model rows to the API's response DTOs. */
public final class InsightsMapper {
    private static final String COVER_DECLINED = "DECLINED";

    private InsightsMapper() {
    }

    /** The SLA state is relative to {@code now}: at risk within {@code atRiskWindow} of the deadline. */
    public static ClaimItemResponse toClaimItemResponse(ClaimRow r, Instant now, Duration atRiskWindow) {
        return new ClaimItemResponse(r.claimId(), r.claimNumber(), r.market(), r.lineOfBusiness(), r.policyNumber(),
                r.status(), COVER_DECLINED.equals(r.coverStatus()), r.handlerId(), r.teamId(), r.submittedAt(), r.slaDueAt(),
                SlaState.of(r.slaDueAt(), r.slaRunning(), now, atRiskWindow),
                toMoneyResponse(r.reserveOutstandingMinor(), r.currency()));
    }

    public static ApprovalItemResponse toApprovalItemResponse(ApprovalRow a) {
        return new ApprovalItemResponse(a.claimId(), a.claimNumber(), a.market(), a.lineOfBusiness(), a.decisionId(),
                a.outcome(), toMoneyResponse(a.amountMinor(), a.currency()), a.proposedBy(), a.proposedAt(), a.teamId());
    }

    public static WorkloadResponse toWorkloadResponse(WorkloadRow w) {
        return new WorkloadResponse(w.handlerId(), w.openClaims(), w.byStatus(), w.oldestSubmittedAt());
    }

    public static PerformanceResponse toPerformanceResponse(PerformanceRow p) {
        return new PerformanceResponse(p.handlerId(), p.closedClaims(), p.averageCycleHours(), p.slaMetPercent(),
                p.reopenRatePercent());
    }

    public static ExposureItemResponse toExposureItemResponse(ExposureRow e) {
        return new ExposureItemResponse(e.market(), e.lineOfBusiness(), e.outstandingClaims(),
                toMoneyResponse(e.totalReserveMinor(), e.currency()));
    }

    static MoneyResponse toMoneyResponse(long minor, String currency) {
        return new MoneyResponse(Money.ofMinor(minor, currency).toMajor().toPlainString(), minor, currency);
    }
}
