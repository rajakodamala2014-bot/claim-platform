package com.claims.platform.claims.intake.api;

import com.claims.platform.claims.core.application.ClaimantViews;
import com.claims.platform.claims.core.application.DocumentStorage.UploadTarget;
import com.claims.platform.claims.core.application.Page;
import com.claims.platform.claims.core.application.PageRequest;
import com.claims.platform.claims.core.domain.Actor;
import com.claims.platform.claims.core.domain.Channel;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.ClaimStatus;
import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.InvalidTransitionException;
import com.claims.platform.claims.core.domain.LineOfBusiness;
import com.claims.platform.claims.core.domain.MarketCode;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.handling.api.ClaimantInformationRequestController;
import com.claims.platform.claims.handling.application.MyInformationRequests;
import com.claims.platform.claims.handling.application.RespondToInformationRequest;
import com.claims.platform.claims.intake.application.AppealMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaim;
import com.claims.platform.claims.intake.application.GetMyClaims;
import com.claims.platform.claims.intake.application.ListMyPolicies;
import com.claims.platform.claims.intake.application.RequestUpload;
import com.claims.platform.claims.intake.application.SubmitClaim;
import com.claims.platform.claims.intake.application.WithdrawMyClaim;
import com.claims.platform.claims.intake.domain.MotorIncident;
import com.claims.platform.claims.platform.infrastructure.SecurityConfig;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests of the claimant surface: request mapping, validation, security and problem+json for every
 * error class. Use cases are mocked; their logic is tested elsewhere.
 */
@WebMvcTest(controllers = {ClaimantClaimController.class, ClaimantPolicyController.class, ClaimantUploadController.class,
        ClaimantInformationRequestController.class},
        properties = "platform.security.dev-secret=test-secret-test-secret-test-secret-123")
@Import({SecurityConfig.class, PlatformSecurityAutoConfiguration.class, PlatformWebAutoConfiguration.class})
class ClaimantApiTest {
    private static final UUID CLAIM = UUID.fromString("3f6c2a4e-1b7d-4c55-9a43-0e2f8d9b1c77");
    private static final String VALID_FNOL = """
            {"policyNumber":"SG-MTR-POL-1001","lineOfBusiness":"MOTOR","incidentAt":"2026-11-13T09:00:00Z",
             "incident":{"peril":"COLLISION","description":"Rear-ended","location":"Orchard Rd",
               "vehicleRegistration":"SBA1234X","driverName":"Priya Nair","damageDescription":"Bumper",
               "thirdParties":[{"name":"John Lim","vehicleRegistration":"SGX9876Z"}]},
             "claimedAmount":{"amount":"6000.00","currency":"SGD"},
             "uploadIds":[],"contact":{"phone":"+6591234567"}}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SubmitClaim submitClaim;
    @MockitoBean
    GetMyClaims getMyClaims;
    @MockitoBean
    GetMyClaim getMyClaim;
    @MockitoBean
    WithdrawMyClaim withdrawMyClaim;
    @MockitoBean
    AppealMyClaim appealMyClaim;
    @MockitoBean
    ListMyPolicies listMyPolicies;
    @MockitoBean
    RequestUpload requestUpload;
    @MockitoBean
    MyInformationRequests myInformationRequests;
    @MockitoBean
    RespondToInformationRequest respond;

    private static JwtRequestPostProcessor claimant() {
        return jwt().jwt(j -> j.subject("cust-priya").claim("roles", List.of("CLAIMANT")).claim("markets", List.of("SG"))
                .claim("name", "Priya Nair")).authorities(new SimpleGrantedAuthority("ROLE_CLAIMANT"));
    }

    private static JwtRequestPostProcessor handler() {
        return jwt().jwt(j -> j.subject("h-arun").claim("roles", List.of("HANDLER")).claim("markets", List.of("SG")))
                .authorities(new SimpleGrantedAuthority("ROLE_HANDLER"));
    }

    // ------------------------------------------------------------------ C3 submit

    @Test
    void submitReturns201WithLocationAndMapsTheRequest() throws Exception {
        UUID key = UUID.randomUUID();
        when(submitClaim.submit(any(), eq(key), any()))
                .thenReturn(new SubmitClaim.Result(CLAIM, "SG-MTR-2026-000001", "TRIAGED", "CONFIRMED", false));

        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/claimant/v1/claims/" + CLAIM))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.claimNumber").value("SG-MTR-2026-000001"))
                .andExpect(jsonPath("$.status").value("TRIAGED"));

        ArgumentCaptor<SubmitClaim.Command> command = ArgumentCaptor.forClass(SubmitClaim.Command.class);
        verify(submitClaim).submit(eq(new Actor("cust-priya", com.claims.platform.claims.core.domain.Role.CLAIMANT)),
                eq(key), command.capture());
        assertThat(command.getValue().claimantId()).isEqualTo("cust-priya");
        assertThat(command.getValue().claimantName()).isEqualTo("Priya Nair");
        assertThat(command.getValue().homeMarket()).isEqualTo(MarketCode.of("SG"));
        assertThat(command.getValue().channel()).isEqualTo(Channel.APP);
        assertThat(command.getValue().lineOfBusiness()).isEqualTo(LineOfBusiness.MOTOR);
        assertThat(command.getValue().incident()).isInstanceOf(MotorIncident.class);
        assertThat(command.getValue().claimedAmount().amountMinor()).isEqualTo(600_000);
    }

    @Test
    void missingIdempotencyKeyIs400WithItsOwnCode() throws Exception {
        mvc.perform(post("/claimant/v1/claims").with(claimant()).contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        verifyNoInteractions(submitClaim);
    }

    @Test
    void beanValidationErrorsListEachField() throws Exception {
        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lineOfBusiness\":\"MOTOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "policyNumber", "incidentAt", "incident")));
    }

    @Test
    void malformedJsonAndUnknownEnumAre400() throws Exception {
        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL.replace("\"MOTOR\"", "\"BOAT\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidMoneyIsAFieldError() throws Exception {
        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL.replace("\"6000.00\"", "\"6000.001\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("claimedAmount"));
    }

    @Test
    void domainValidationAndBusinessErrorsMapToStableCodes() throws Exception {
        when(submitClaim.submit(any(), any(), any()))
                .thenThrow(new ValidationException(List.of(new ValidationException.FieldViolation("incident.peril", "must be one of"))))
                .thenThrow(new DomainException(ErrorCode.POLICY_NOT_IN_FORCE, "Policy expired"))
                .thenThrow(new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "reused"));

        for (String[] expected : List.of(new String[]{"400", "VALIDATION_FAILED"}, new String[]{"422", "POLICY_NOT_IN_FORCE"},
                new String[]{"422", "IDEMPOTENCY_KEY_REUSED"})) {
            mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL))
                    .andExpect(status().is(Integer.parseInt(expected[0])))
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value(expected[1]))
                    .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.startsWith("https://errors.claims.example/")))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
        }
    }

    // ------------------------------------------------------------------ security

    @Test
    void anonymousIs401AndStaffAre403OnTheClaimantSurface() throws Exception {
        mvc.perform(get("/claimant/v1/claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/claimant/v1/claims").with(handler()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(getMyClaims);
    }

    // ------------------------------------------------------------------ C4-C6

    @Test
    void listPassesCursorAndRejectsBadLimits() throws Exception {
        when(getMyClaims.list(eq("cust-priya"), any())).thenReturn(new Page<>(List.of(new GetMyClaims.Item(CLAIM,
                "SG-MTR-2026-000001", "AWAITING_INFO", "Action needed", "MOTOR", "SG-MTR-POL-1001",
                Instant.parse("2026-11-14T02:00:00Z"), Instant.parse("2026-11-14T03:00:00Z"), true)), "next-cursor"));

        mvc.perform(get("/claimant/v1/claims").with(claimant()).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionNeeded").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"));
        mvc.perform(get("/claimant/v1/claims").with(claimant()).param("limit", "1000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("limit"));
        mvc.perform(get("/claimant/v1/claims").with(claimant()).param("cursor", "garbage!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cursor"));
    }

    @Test
    void detailCarriesEtagAndOthersClaimsAre404() throws Exception {
        ClaimantViews.ClaimView view = new ClaimantViews.ClaimView(CLAIM, "SG-MTR-2026-000001", "TRIAGED",
                "Waiting for a claims handler", "A claims handler will review your claim shortly.", "MOTOR",
                "SG-MTR-POL-1001", Instant.parse("2026-11-13T09:00:00Z"), Instant.parse("2026-11-14T02:00:00Z"),
                Instant.parse("2026-11-14T02:00:00Z"), null, null, null, List.of(), true, false);
        when(getMyClaim.detail(new ClaimId(CLAIM), "cust-priya")).thenReturn(new GetMyClaim.Detail(view, 3));
        UUID other = UUID.randomUUID();
        when(getMyClaim.detail(new ClaimId(other), "cust-priya"))
                .thenThrow(new DomainException(ErrorCode.CLAIM_NOT_FOUND, "Claim not found"));

        mvc.perform(get("/claimant/v1/claims/{id}", CLAIM).with(claimant()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.statusLabel").value("Waiting for a claims handler"))
                .andExpect(jsonPath("$.canWithdraw").value(true))
                .andExpect(jsonPath("$.decision").doesNotExist());
        mvc.perform(get("/claimant/v1/claims/{id}", other).with(claimant()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
        mvc.perform(get("/claimant/v1/claims/{id}", "not-a-uuid").with(claimant()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void timelineReturnsEntries() throws Exception {
        when(getMyClaim.timeline(new ClaimId(CLAIM), "cust-priya")).thenReturn(List.of(new ClaimantViews.TimelineEntry(
                Instant.parse("2026-11-14T02:00:00Z"), "ClaimSubmitted", "Claim received", Map.of("claimNumber", "SG-MTR-2026-000001"))));

        mvc.perform(get("/claimant/v1/claims/{id}/timeline", CLAIM).with(claimant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Claim received"));
    }

    // ------------------------------------------------------------------ C9, C10

    @Test
    void withdrawAndAppealMapTransitionsAndConflicts() throws Exception {
        when(withdrawMyClaim.withdraw(eq(new ClaimId(CLAIM)), any(), any())).thenReturn("WITHDRAWN")
                .thenThrow(new InvalidTransitionException(ClaimStatus.DECISION_PENDING, ClaimStatus.WITHDRAWN));

        mvc.perform(post("/claimant/v1/claims/{id}/withdraw", CLAIM).with(claimant()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(post("/claimant/v1/claims/{id}/withdraw", CLAIM).with(claimant()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_INVALID_TRANSITION"))
                .andExpect(jsonPath("$.detail").value("Claim cannot move from DECISION_PENDING to WITHDRAWN"));
        mvc.perform(post("/claimant/v1/claims/{id}/appeal", CLAIM).with(claimant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
    }

    // ------------------------------------------------------------------ C1, C2, C7, C8

    @Test
    void policySystemOutageIs503() throws Exception {
        when(listMyPolicies.list("cust-priya"))
                .thenThrow(new DomainException(ErrorCode.POLICY_SYSTEM_UNAVAILABLE, "temporarily unavailable"));

        mvc.perform(get("/claimant/v1/policies").with(claimant()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POLICY_SYSTEM_UNAVAILABLE"));
    }

    @Test
    void uploadReturnsPresignedTarget() throws Exception {
        UUID upload = UUID.randomUUID();
        when(requestUpload.request("cust-priya", "photo.jpg", "image/jpeg", 2048)).thenReturn(new RequestUpload.Result(upload,
                new UploadTarget("http://x/claimant/v1/uploads/" + upload + "/content?expires=1&signature=abc", "PUT",
                        Map.of("Content-Type", "image/jpeg"), Instant.parse("2026-11-14T02:15:00Z"))));

        mvc.perform(post("/claimant/v1/uploads").with(claimant()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":2048}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").value(upload.toString()))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.headers.Content-Type").value("image/jpeg"));
        mvc.perform(post("/claimant/v1/uploads").with(claimant()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"\",\"contentType\":\"image/jpeg\",\"sizeBytes\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void infoRequestResponseAndUnknownRequest() throws Exception {
        UUID request = UUID.randomUUID();
        when(respond.respond(eq(new ClaimId(CLAIM)), eq(request), any(), anyString(), any())).thenReturn("UNDER_ASSESSMENT");
        when(respond.respond(eq(new ClaimId(CLAIM)), eq(CLAIM), any(), any(), any()))
                .thenThrow(new DomainException(ErrorCode.INFO_REQUEST_NOT_FOUND, "No open information request"));

        mvc.perform(post("/claimant/v1/claims/{id}/info-requests/{rid}/responses", CLAIM, request).with(claimant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"Report attached\",\"uploadIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));
        mvc.perform(post("/claimant/v1/claims/{id}/info-requests/{rid}/responses", CLAIM, CLAIM).with(claimant())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uploadIds\":[]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INFO_REQUEST_NOT_FOUND"));
    }

    @Test
    void unexpectedErrorsAre500WithoutLeakingDetails() throws Exception {
        when(listMyPolicies.list("cust-priya")).thenThrow(new IllegalStateException("db password=hunter2"));

        mvc.perform(get("/claimant/v1/policies").with(claimant()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
