package com.claims.platform.insights.api;

import com.claims.platform.common.money.Money;
import com.claims.platform.insights.application.InsightsQueries.ApprovalRow;
import com.claims.platform.insights.application.InsightsQueries.ClaimRow;
import com.claims.platform.insights.application.InsightsQueries.ExposureRow;
import com.claims.platform.insights.application.InsightsQueries.PerformanceRow;
import com.claims.platform.insights.application.InsightsQueries.QueueView;
import com.claims.platform.insights.application.InsightsQueries.WorkloadRow;
import com.claims.platform.insights.application.StaffInsights;
import com.claims.platform.insights.application.Viewer;
import com.claims.platform.insights.domain.SlaState;
import com.claims.platform.web.security.PlatformUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** I1-I7. Read-only; data may lag Claims by a few seconds. */
@RestController
public class InsightsController {
    private final StaffInsights insights;
    private final Clock clock;

    public InsightsController(StaffInsights insights, Clock clock) {
        this.insights = insights;
        this.clock = clock;
    }

    public record MoneyView(String amount, long amountMinor, String currency) {
        static MoneyView of(long minor, String currency) {
            return new MoneyView(Money.ofMinor(minor, currency).toMajor().toPlainString(), minor, currency);
        }
    }

    public record ClaimItem(UUID claimId, String claimNumber, String market, String lineOfBusiness, String policyNumber,
                            String status, boolean coverDeclined, String handlerId, String teamId, Instant submittedAt,
                            Instant slaDueAt, SlaState slaState, MoneyView reserve) {
    }

    public record ClaimPage(List<ClaimItem> items, String nextCursor) {
    }

    public record ItemList<T>(List<T> items) {
    }

    public record ApprovalItem(UUID claimId, String claimNumber, String market, String lineOfBusiness, UUID decisionId,
                               String outcome, MoneyView amount, String proposedBy, Instant proposedAt, String teamId) {
    }

    public record ExposureItem(String market, String lineOfBusiness, int outstandingClaims, MoneyView totalReserve) {
    }

    /** I1. view = unassigned | mine | team; sorted by SLA deadline. */
    @GetMapping("/staff/v1/work-queue")
    public ClaimPage workQueue(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "unassigned") String view,
                               @RequestParam(required = false) String market, @RequestParam(required = false) String lob,
                               @RequestParam(required = false, defaultValue = "sla") String sort,
                               @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        if (!"sla".equals(sort)) {
            throw new com.claims.platform.insights.application.InsightsException(
                    com.claims.platform.insights.application.InsightsException.Code.VALIDATION_FAILED, "sort must be sla");
        }
        var page = insights.workQueue(viewer(jwt), queueView(view), market, upper(lob), cursor, limit);
        return new ClaimPage(page.items().stream().map(this::item).toList(), page.nextCursor());
    }

    /** I2. */
    @GetMapping("/staff/v1/search")
    public ItemList<ClaimItem> search(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String status, @RequestParam(required = false) String market,
                                      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return new ItemList<>(insights.search(viewer(jwt), q, upper(status), market, from, to).stream().map(this::item).toList());
    }

    /** I3. */
    @GetMapping("/staff/v1/approvals")
    public ItemList<ApprovalItem> approvals(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String market) {
        return new ItemList<>(insights.approvals(viewer(jwt), market).stream().map(InsightsController::approval).toList());
    }

    /** I4. */
    @GetMapping("/staff/v1/insights/workload")
    public ItemList<WorkloadRow> workload(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String team) {
        return new ItemList<>(insights.workload(viewer(jwt), team));
    }

    /** I5. */
    @GetMapping("/staff/v1/insights/performance")
    public ItemList<PerformanceRow> performance(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String team,
                                                @RequestParam(required = false) Instant from,
                                                @RequestParam(required = false) Instant to) {
        return new ItemList<>(insights.performance(viewer(jwt), team, from, to));
    }

    /** I6. */
    @GetMapping("/staff/v1/insights/sla")
    public ItemList<ClaimItem> sla(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String market) {
        return new ItemList<>(insights.sla(viewer(jwt), market).stream().map(this::item).toList());
    }

    /** I7. */
    @GetMapping("/staff/v1/insights/exposure")
    public ItemList<ExposureItem> exposure(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String market,
                                           @RequestParam(required = false) String lob) {
        return new ItemList<>(insights.exposure(viewer(jwt), market, upper(lob)).stream()
                .map(e -> new ExposureItem(e.market(), e.lineOfBusiness(), e.outstandingClaims(),
                        MoneyView.of(e.totalReserveMinor(), e.currency())))
                .toList());
    }

    private ClaimItem item(ClaimRow r) {
        return new ClaimItem(r.claimId(), r.claimNumber(), r.market(), r.lineOfBusiness(), r.policyNumber(), r.status(),
                "DECLINED".equals(r.coverStatus()), r.handlerId(), r.teamId(), r.submittedAt(), r.slaDueAt(),
                SlaState.of(r.slaDueAt(), r.slaRunning(), clock.instant(), insights.atRiskWindow()),
                MoneyView.of(r.reserveOutstandingMinor(), r.currency()));
    }

    private static ApprovalItem approval(ApprovalRow a) {
        return new ApprovalItem(a.claimId(), a.claimNumber(), a.market(), a.lineOfBusiness(), a.decisionId(), a.outcome(),
                MoneyView.of(a.amountMinor(), a.currency()), a.proposedBy(), a.proposedAt(), a.teamId());
    }

    private static Viewer viewer(Jwt jwt) {
        PlatformUser user = PlatformUser.from(jwt);
        return new Viewer(user.id(), user.primaryRole(), user.markets(), user.teamId());
    }

    private static QueueView queueView(String view) {
        try {
            return QueueView.valueOf(view.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.claims.platform.insights.application.InsightsException(
                    com.claims.platform.insights.application.InsightsException.Code.VALIDATION_FAILED,
                    "view must be unassigned, mine or team");
        }
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
