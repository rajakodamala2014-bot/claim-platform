package com.claims.platform.insights.controller;

import com.claims.platform.insights.common.StaffViewer;
import com.claims.platform.insights.dto.response.ApprovalItemResponse;
import com.claims.platform.insights.dto.response.ClaimItemResponse;
import com.claims.platform.insights.dto.response.ClaimPageResponse;
import com.claims.platform.insights.dto.response.ExposureItemResponse;
import com.claims.platform.insights.dto.response.ItemListResponse;
import com.claims.platform.insights.dto.response.PerformanceResponse;
import com.claims.platform.insights.dto.response.WorkloadResponse;
import com.claims.platform.insights.entity.ClaimRow;
import com.claims.platform.insights.entity.ClaimRowPage;
import com.claims.platform.insights.entity.QueueView;
import com.claims.platform.insights.exception.InsightsException;
import com.claims.platform.insights.mapper.InsightsMapper;
import com.claims.platform.insights.service.InsightsQueryService;
import com.claims.platform.web.common.PlatformUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** I1-I7. Read-only; data may lag Claims by a few seconds. */
@RestController
@Tag(name = "Staff insights", description = "Work queues, search, approvals inbox and team dashboards (read-only, a few seconds behind Claims)")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class InsightsController {
    private static final String SUPPORTED_SORT = "sla";

    private final InsightsQueryService insightsService;
    private final Clock clock;

    public InsightsController(InsightsQueryService insightsService, Clock clock) {
        this.insightsService = insightsService;
        this.clock = clock;
    }

    @GetMapping("/staff/v1/work-queue")
    @Operation(summary = "Get my work queue", description = "I1. Sorted by SLA deadline, oldest first; cursor paginated.")
    @ApiResponse(responseCode = "200", description = "One page of the queue")
    @ApiResponse(responseCode = "400", description = "Unknown view or sort, invalid cursor or limit (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "403", description = "Not staff, not assigned to the market (FORBIDDEN_MARKET)")
    public ClaimPageResponse getWorkQueue(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Which queue", schema = @Schema(allowableValues = {"unassigned", "mine", "team"}))
            @RequestParam(defaultValue = "unassigned") String view,
            @Parameter(description = "One of my markets; all of them when omitted", example = "SG")
            @RequestParam(required = false) String market,
            @Parameter(description = "Line of business", schema = @Schema(allowableValues = {"MOTOR", "PROPERTY"}))
            @RequestParam(required = false) String lob,
            @Parameter(description = "Only sla is supported") @RequestParam(required = false, defaultValue = SUPPORTED_SORT) String sort,
            @Parameter(description = "nextCursor of the previous page") @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size, 1-100 (default 20)") @RequestParam(required = false) Integer limit) {
        if (!SUPPORTED_SORT.equals(sort)) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "sort must be sla");
        }
        ClaimRowPage page = insightsService.getWorkQueue(viewerOf(jwt), parseQueueView(view), market, toUpperOrNull(lob),
                cursor, limit);
        return new ClaimPageResponse(toClaimItems(page.items()), page.nextCursor());
    }

    @GetMapping("/staff/v1/search")
    @Operation(summary = "Search claims", description = "I2. At least one of q, status, from, to is required; at most 50 results.")
    @ApiResponse(responseCode = "200", description = "Matching claims")
    @ApiResponse(responseCode = "400", description = "No search criterion given (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "403", description = "Not staff, not assigned to the market (FORBIDDEN_MARKET)")
    public ItemListResponse<ClaimItemResponse> searchClaims(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Part of a claim or policy number, case-insensitive") @RequestParam(required = false) String q,
            @Parameter(description = "Claim status", example = "UNDER_ASSESSMENT") @RequestParam(required = false) String status,
            @Parameter(description = "One of my markets; all of them when omitted") @RequestParam(required = false) String market,
            @Parameter(description = "Submitted at or after") @RequestParam(required = false) Instant from,
            @Parameter(description = "Submitted before") @RequestParam(required = false) Instant to) {
        return new ItemListResponse<>(toClaimItems(
                insightsService.searchClaims(viewerOf(jwt), q, toUpperOrNull(status), market, from, to)));
    }

    @GetMapping("/staff/v1/approvals")
    @Operation(summary = "List decisions waiting for my approval",
            description = "I3. Team leads see their team, managers every team in their markets. The caller's own proposals are left out.")
    @ApiResponse(responseCode = "200", description = "Decisions awaiting approval")
    @ApiResponse(responseCode = "403", description = "Not a team lead or manager (FORBIDDEN_ROLE), or not assigned to the market (FORBIDDEN_MARKET)")
    public ItemListResponse<ApprovalItemResponse> listPendingApprovals(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of my markets; all of them when omitted") @RequestParam(required = false) String market) {
        return new ItemListResponse<>(insightsService.listPendingApprovals(viewerOf(jwt), market).stream()
                .map(InsightsMapper::toApprovalItemResponse).toList());
    }

    @GetMapping("/staff/v1/insights/workload")
    @Operation(summary = "Get the open workload per handler of a team", description = "I4.")
    @ApiResponse(responseCode = "200", description = "Workload per handler")
    @ApiResponse(responseCode = "400", description = "No team given and the caller has none (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "403", description = "Not a team lead or manager, or another team than the caller's own (FORBIDDEN_ROLE)")
    public ItemListResponse<WorkloadResponse> getWorkload(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Team id; defaults to the caller's team (managers may ask for any)")
            @RequestParam(required = false) String team) {
        return new ItemListResponse<>(insightsService.getWorkload(viewerOf(jwt), team).stream()
                .map(InsightsMapper::toWorkloadResponse).toList());
    }

    @GetMapping("/staff/v1/insights/performance")
    @Operation(summary = "Get closed-claim performance per handler of a team", description = "I5. Defaults to the last 30 days.")
    @ApiResponse(responseCode = "200", description = "Performance per handler")
    @ApiResponse(responseCode = "400", description = "from is not before to, or no team (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "403", description = "Not a team lead or manager, or another team than the caller's own (FORBIDDEN_ROLE)")
    public ItemListResponse<PerformanceResponse> getPerformance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Team id; defaults to the caller's team") @RequestParam(required = false) String team,
            @Parameter(description = "Closed at or after; default 30 days before to") @RequestParam(required = false) Instant from,
            @Parameter(description = "Closed before; default now") @RequestParam(required = false) Instant to) {
        return new ItemListResponse<>(insightsService.getPerformance(viewerOf(jwt), team, from, to).stream()
                .map(InsightsMapper::toPerformanceResponse).toList());
    }

    @GetMapping("/staff/v1/insights/sla")
    @Operation(summary = "List claims at risk of, or past, their SLA", description = "I6. Earliest deadline first.")
    @ApiResponse(responseCode = "200", description = "Claims at risk or breached")
    @ApiResponse(responseCode = "403", description = "Not a team lead or manager (FORBIDDEN_ROLE), or not assigned to the market (FORBIDDEN_MARKET)")
    public ItemListResponse<ClaimItemResponse> listSlaWatch(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of my markets; all of them when omitted") @RequestParam(required = false) String market) {
        return new ItemListResponse<>(toClaimItems(insightsService.listSlaWatch(viewerOf(jwt), market)));
    }

    @GetMapping("/staff/v1/insights/exposure")
    @Operation(summary = "Get outstanding reserves by market and line of business", description = "I7.")
    @ApiResponse(responseCode = "200", description = "Exposure per market, line of business and currency")
    @ApiResponse(responseCode = "403", description = "Not a team lead or manager (FORBIDDEN_ROLE), or not assigned to the market (FORBIDDEN_MARKET)")
    public ItemListResponse<ExposureItemResponse> getExposure(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of my markets; all of them when omitted") @RequestParam(required = false) String market,
            @Parameter(description = "Line of business", schema = @Schema(allowableValues = {"MOTOR", "PROPERTY"}))
            @RequestParam(required = false) String lob) {
        return new ItemListResponse<>(insightsService.getExposure(viewerOf(jwt), market, toUpperOrNull(lob)).stream()
                .map(InsightsMapper::toExposureItemResponse).toList());
    }

    private List<ClaimItemResponse> toClaimItems(List<ClaimRow> rows) {
        Instant now = clock.instant();
        return rows.stream().map(row -> InsightsMapper.toClaimItemResponse(row, now, insightsService.atRiskWindow())).toList();
    }

    private static StaffViewer viewerOf(Jwt jwt) {
        PlatformUser user = PlatformUser.from(jwt);
        return new StaffViewer(user.id(), user.primaryRole(), user.markets(), user.teamId());
    }

    private static QueueView parseQueueView(String view) {
        try {
            return QueueView.valueOf(view.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InsightsException(InsightsException.Code.VALIDATION_FAILED, "view must be unassigned, mine or team");
        }
    }

    private static String toUpperOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
