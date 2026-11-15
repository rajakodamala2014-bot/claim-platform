package com.claims.platform.claims.handling.api;

import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.application.StaffContext;
import com.claims.platform.claims.core.domain.ApprovalRequirement;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Decision;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.Payee;
import com.claims.platform.claims.core.domain.Settlement;
import com.claims.platform.claims.handling.application.AssignClaim;
import com.claims.platform.claims.handling.application.ProposeDecision;
import com.claims.platform.claims.handling.application.RecordAssessment;
import com.claims.platform.claims.handling.application.RequestInformation;
import com.claims.platform.claims.handling.application.ReviewDecision;
import com.claims.platform.claims.handling.application.SetReserve;
import com.claims.platform.claims.handling.application.StartAssessment;
import com.claims.platform.claims.intake.api.StaffClaimController;
import com.claims.platform.claims.intake.application.GetStaffClaim;
import com.claims.platform.claims.intake.application.SubmitClaim;
import com.claims.platform.claims.platform.infrastructure.SecurityConfig;
import com.claims.platform.claims.settlement.api.PaymentsWebhookController;
import com.claims.platform.claims.settlement.api.StaffSettlementController;
import com.claims.platform.claims.settlement.application.InstructSettlement;
import com.claims.platform.claims.settlement.application.RecordPaymentResult;
import com.claims.platform.claims.settlement.application.RetrySettlement;
import com.claims.platform.common.money.Money;
import com.claims.platform.web.PlatformWebAutoConfiguration;
import com.claims.platform.web.security.PlatformSecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice tests of the staff surface and the payments webhook: If-Match/ETag, 200 vs 202, scoping, errors. */
@WebMvcTest(controllers = {StaffHandlingController.class, StaffSettlementController.class, StaffClaimController.class,
        PaymentsWebhookController.class},
        properties = {"platform.security.dev-secret=test-secret-test-secret-test-secret-123",
                "claims.payments.webhook-secret=webhook-secret-webhook-secret-0123456789"})
@Import({SecurityConfig.class, PlatformSecurityAutoConfiguration.class, PlatformWebAutoConfiguration.class})
class StaffApiTest {
    private static final UUID CLAIM = UUID.fromString("3f6c2a4e-1b7d-4c55-9a43-0e2f8d9b1c77");
    private static final String WEBHOOK_SECRET = "webhook-secret-webhook-secret-0123456789";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AssignClaim assignClaim;
    @MockitoBean
    StartAssessment startAssessment;
    @MockitoBean
    SetReserve setReserve;
    @MockitoBean
    RequestInformation requestInformation;
    @MockitoBean
    RecordAssessment recordAssessment;
    @MockitoBean
    ProposeDecision proposeDecision;
    @MockitoBean
    ReviewDecision reviewDecision;
    @MockitoBean
    InstructSettlement instructSettlement;
    @MockitoBean
    RetrySettlement retrySettlement;
    @MockitoBean
    SubmitClaim submitClaim;
    @MockitoBean
    GetStaffClaim getStaffClaim;
    @MockitoBean
    RecordPaymentResult recordPaymentResult;

    private static JwtRequestPostProcessor staff(String sub, String role, String... markets) {
        return jwt().jwt(j -> j.subject(sub).claim("roles", List.of(role)).claim("markets", List.of(markets))
                .claim("team", "team-sg-motor")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static JwtRequestPostProcessor arun() {
        return staff("h-arun", "HANDLER", "SG");
    }

    private static <T> Outcome<T> outcome(T value, String status, long version) {
        return new Outcome<>(value, new ClaimId(CLAIM), status, version);
    }

    @Test
    void stateChangeWithoutIfMatchIs428AndNeverReachesTheUseCase() throws Exception {
        mvc.perform(post("/staff/v1/claims/{id}/assignment", CLAIM).with(arun()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IF_MATCH_REQUIRED"));
        mvc.perform(post("/staff/v1/claims/{id}/assignment", CLAIM).with(arun()).header("If-Match", "W/\"3\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("If-Match"));
        verifyNoInteractions(assignClaim);
    }

    @Test
    void pickUpPassesVersionAndStaffContextAndReturnsNewEtag() throws Exception {
        when(assignClaim.assign(eq(new ClaimId(CLAIM)), any(), eq(3L), isNull(), isNull()))
                .thenReturn(outcome("h-arun", "ASSIGNED", 4));

        mvc.perform(post("/staff/v1/claims/{id}/assignment", CLAIM).with(arun()).header("If-Match", "\"3\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.result").value("h-arun"));

        ArgumentCaptor<StaffContext> staff = ArgumentCaptor.forClass(StaffContext.class);
        verify(assignClaim).assign(any(), staff.capture(), eq(3L), isNull(), isNull());
        assertThat(staff.getValue().actor().id()).isEqualTo("h-arun");
        assertThat(staff.getValue().markets()).containsExactly(MarketCode.of("SG"));
        assertThat(staff.getValue().teamId()).isEqualTo("team-sg-motor");
    }

    @Test
    void staleVersionAlreadyAssignedAndForeignMarketMapToTheirStatuses() throws Exception {
        when(assignClaim.assign(any(), any(), anyLong(), any(), any()))
                .thenThrow(new DomainException(ErrorCode.STALE_VERSION, "stale"))
                .thenThrow(new DomainException(ErrorCode.CLAIM_ALREADY_ASSIGNED, "taken"))
                .thenThrow(new DomainException(ErrorCode.FORBIDDEN_MARKET, "not your market"));

        for (Object[] expected : List.of(new Object[]{412, "STALE_VERSION"}, new Object[]{409, "CLAIM_ALREADY_ASSIGNED"},
                new Object[]{403, "FORBIDDEN_MARKET"})) {
            mvc.perform(post("/staff/v1/claims/{id}/assignment", CLAIM).with(arun()).header("If-Match", "\"1\""))
                    .andExpect(status().is((Integer) expected[0]))
                    .andExpect(jsonPath("$.code").value(expected[1]));
        }
    }

    @Test
    void customersCannotUseTheStaffSurface() throws Exception {
        mvc.perform(get("/staff/v1/claims/{id}", CLAIM).with(jwt().jwt(j -> j.subject("cust-priya"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CLAIMANT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(getStaffClaim);
    }

    @Test
    void decisionWithinAuthorityIs200AboveIs202() throws Exception {
        Decision fin = decision(ApprovalRequirement.NONE, Decision.Status.FINAL);
        Decision waiting = decision(ApprovalRequirement.SUPERVISOR_APPROVAL, Decision.Status.AWAITING_APPROVAL);
        when(proposeDecision.propose(any(), any(), anyLong(), eq(Decision.Outcome.APPROVE), any(), any()))
                .thenReturn(outcome(fin, "APPROVED", 6)).thenReturn(outcome(waiting, "DECISION_PENDING", 6));
        String body = "{\"outcome\":\"APPROVE\",\"amount\":{\"amount\":\"4200.00\",\"currency\":\"SGD\"}}";

        mvc.perform(post("/staff/v1/claims/{id}/decisions", CLAIM).with(arun()).header("If-Match", "\"5\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.approvalRequired").value(false))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/staff/v1/claims/{id}/decisions", CLAIM).with(arun()).header("If-Match", "\"5\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.approvalRequired").value(true))
                .andExpect(jsonPath("$.result.decisionStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.result.amount.amount").value("4200.00"));
    }

    @Test
    void approvalNeedsAnAmountAndAuthorityErrorsAre403() throws Exception {
        mvc.perform(post("/staff/v1/claims/{id}/decisions", CLAIM).with(arun()).header("If-Match", "\"5\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("amount"));
        when(reviewDecision.review(any(), any(), any(), anyLong(), any(), any()))
                .thenThrow(new DomainException(ErrorCode.SELF_APPROVAL_NOT_ALLOWED, "own"))
                .thenThrow(new DomainException(ErrorCode.AMOUNT_EXCEEDS_AUTHORITY, "too much"));
        for (String code : List.of("SELF_APPROVAL_NOT_ALLOWED", "AMOUNT_EXCEEDS_AUTHORITY")) {
            mvc.perform(post("/staff/v1/claims/{id}/decisions/{d}/approval", CLAIM, UUID.randomUUID())
                            .with(staff("tl-meera", "TEAM_LEAD", "SG")).header("If-Match", "\"6\"")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void settlementValidatesPayeeAndMapsExceedsApproved() throws Exception {
        mvc.perform(post("/staff/v1/claims/{id}/settlements", CLAIM).with(arun()).header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payee\":{\"name\":\"Priya\",\"bankCode\":\"DBS\",\"accountNumber\":\"12; DROP\"},"
                                + "\"amount\":{\"amount\":\"4200\",\"currency\":\"SGD\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("payee.accountNumber"));
        when(instructSettlement.instruct(any(), any(), anyLong(), any(), any()))
                .thenThrow(new DomainException(ErrorCode.SETTLEMENT_EXCEEDS_APPROVED, "too much"))
                .thenReturn(outcome(new Settlement(UUID.randomUUID(), Money.of("4200", "SGD"),
                        new Payee("Priya", "DBS", "123456789"), UUID.randomUUID(), 1, Settlement.Status.INSTRUCTED,
                        null, null, Instant.now(), null), "SETTLEMENT_IN_PROGRESS", 8));
        String ok = "{\"payee\":{\"name\":\"Priya\",\"bankCode\":\"DBS\",\"accountNumber\":\"123456789\"},"
                + "\"amount\":{\"amount\":\"4200\",\"currency\":\"SGD\"}}";
        mvc.perform(post("/staff/v1/claims/{id}/settlements", CLAIM).with(arun()).header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SETTLEMENT_EXCEEDS_APPROVED"));
        mvc.perform(post("/staff/v1/claims/{id}/settlements", CLAIM).with(arun()).header("If-Match", "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.paymentStatus").value("INSTRUCTED"))
                .andExpect(jsonPath("$.result.attempt").value(1));
    }

    @Test
    void staffRegistrationIsScopedToTheCallersMarkets() throws Exception {
        when(submitClaim.submit(any(), any(), any(), any()))
                .thenReturn(new SubmitClaim.Result(CLAIM, "SG-MTR-2026-000009", "TRIAGED", "CONFIRMED", false));
        String body = """
                {"claimantId":"cust-priya","claimantName":"Priya Nair","policyNumber":"SG-MTR-POL-1001",
                 "lineOfBusiness":"MOTOR","incidentAt":"2026-11-13T09:00:00Z",
                 "incident":{"peril":"THEFT","description":"Stolen","location":"x","vehicleRegistration":"SBA1",
                   "driverName":"Priya Nair","damageDescription":"gone"}}
                """;

        mvc.perform(post("/staff/v1/claims").with(arun()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/staff/v1/claims/" + CLAIM));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<MarketCode>> markets = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<SubmitClaim.Command> command = ArgumentCaptor.forClass(SubmitClaim.Command.class);
        verify(submitClaim).submit(any(), any(), command.capture(), markets.capture());
        assertThat(markets.getValue()).containsExactly(MarketCode.of("SG"));
        assertThat(command.getValue().channel()).isEqualTo(com.claims.platform.claims.core.domain.Channel.STAFF);
        assertThat(command.getValue().claimantId()).isEqualTo("cust-priya");
    }

    @Test
    void webhookRequiresAValidSignature() throws Exception {
        String body = "{\"instructionId\":\"" + UUID.randomUUID() + "\",\"settlementId\":\"" + UUID.randomUUID()
                + "\",\"claimId\":\"" + CLAIM + "\",\"status\":\"PAID\",\"paymentRef\":\"PAY-1\",\"amountMinor\":420000,"
                + "\"currency\":\"SGD\",\"processedAt\":\"2026-11-14T05:00:00Z\"}";
        when(recordPaymentResult.record(any())).thenReturn(RecordPaymentResult.Outcome.APPLIED);
        String good = "sha256=" + PaymentsWebhookController.sign(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), body);

        mvc.perform(post(PaymentsWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        mvc.perform(post(PaymentsWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body + " ")
                        .header("X-Signature", good))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recordPaymentResult);
        mvc.perform(post(PaymentsWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-Signature", good))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("APPLIED"));
    }

    private static Decision decision(ApprovalRequirement approval, Decision.Status status) {
        return new Decision(UUID.randomUUID(), Decision.Outcome.APPROVE, Money.of("4200", "SGD"), null, "h-arun",
                Instant.now(), approval, status, null, null);
    }
}
