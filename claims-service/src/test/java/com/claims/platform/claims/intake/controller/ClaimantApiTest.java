package com.claims.platform.claims.intake.controller;

import com.claims.platform.claims.core.dto.response.TimelineEntryResponse;
import com.claims.platform.claims.core.dto.response.ClaimantClaimResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimSummaryResponse;
import com.claims.platform.claims.intake.dto.response.ClaimantClaimDetailResponse;
import com.claims.platform.claims.intake.dto.response.SubmitClaimResponse;
import com.claims.platform.claims.core.mapper.ClaimantClaimMapper;
import com.claims.platform.claims.core.service.DocumentStorageService.UploadTarget;
import com.claims.platform.claims.core.common.Page;
import com.claims.platform.claims.core.common.PageRequest;
import com.claims.platform.claims.core.entity.Actor;
import com.claims.platform.claims.core.entity.Channel;
import com.claims.platform.claims.core.entity.ClaimId;
import com.claims.platform.claims.core.entity.ClaimStatus;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.exception.InvalidTransitionException;
import com.claims.platform.claims.core.entity.LineOfBusiness;
import com.claims.platform.claims.core.entity.MarketCode;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.handling.controller.ClaimantInformationRequestController;
import com.claims.platform.claims.handling.service.InformationRequestService;
import com.claims.platform.claims.intake.service.ClaimIntakeService;
import com.claims.platform.claims.intake.service.ClaimQueryService;
import com.claims.platform.claims.intake.service.PolicyService;
import com.claims.platform.claims.intake.service.DocumentUploadService;
import com.claims.platform.claims.intake.entity.MotorIncident;
import com.claims.platform.claims.platform.config.SecurityConfig;
import com.claims.platform.web.config.PlatformWebAutoConfiguration;
import com.claims.platform.web.config.PlatformSecurityAutoConfiguration;
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
@WebMvcTest(controllers = {ClaimantClaimController.class, ClaimantPolicyController.class, ClaimantDocumentUploadController.class,
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
    ClaimIntakeService claimIntakeService;
    @MockitoBean
    ClaimQueryService claimQueryService;
    @MockitoBean
    PolicyService policyService;
    @MockitoBean
    DocumentUploadService documentUploadService;
    @MockitoBean
    InformationRequestService informationRequestService;

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
        when(claimIntakeService.submitClaim(any(), eq(key), any()))
                .thenReturn(new SubmitClaimResponse(CLAIM, "SG-MTR-2026-000001", "TRIAGED", "CONFIRMED", false));

        mvc.perform(post("/claimant/v1/claims").with(claimant()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FNOL))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/claimant/v1/claims/" + CLAIM))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.claimNumber").value("SG-MTR-2026-000001"))
                .andExpect(jsonPath("$.status").value("TRIAGED"));

        ArgumentCaptor<ClaimIntakeService.SubmitClaimCommand> command = ArgumentCaptor.forClass(ClaimIntakeService.SubmitClaimCommand.class);
        verify(claimIntakeService).submitClaim(eq(new Actor("cust-priya", com.claims.platform.claims.core.entity.Role.CLAIMANT)),
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
        verifyNoInteractions(claimIntakeService);
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
        when(claimIntakeService.submitClaim(any(), any(), any()))
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
        verifyNoInteractions(claimQueryService);
    }

    // ------------------------------------------------------------------ C4-C6

    @Test
    void listPassesCursorAndRejectsBadLimits() throws Exception {
        when(claimQueryService.listClaimantClaims(eq("cust-priya"), any())).thenReturn(new Page<>(List.of(new ClaimantClaimSummaryResponse(CLAIM,
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
        ClaimantClaimResponse view = new ClaimantClaimResponse(CLAIM, "SG-MTR-2026-000001", "TRIAGED",
                "Waiting for a claims handler", "A claims handler will review your claim shortly.", "MOTOR",
                "SG-MTR-POL-1001", Instant.parse("2026-11-13T09:00:00Z"), Instant.parse("2026-11-14T02:00:00Z"),
                Instant.parse("2026-11-14T02:00:00Z"), null, null, null, List.of(), true, false);
        when(claimQueryService.getClaimantClaim(new ClaimId(CLAIM), "cust-priya")).thenReturn(new ClaimantClaimDetailResponse(view, 3));
        UUID other = UUID.randomUUID();
        when(claimQueryService.getClaimantClaim(new ClaimId(other), "cust-priya"))
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
        when(claimQueryService.getClaimantClaimTimeline(new ClaimId(CLAIM), "cust-priya")).thenReturn(List.of(new TimelineEntryResponse(
                Instant.parse("2026-11-14T02:00:00Z"), "ClaimSubmitted", "Claim received", Map.of("claimNumber", "SG-MTR-2026-000001"))));

        mvc.perform(get("/claimant/v1/claims/{id}/timeline", CLAIM).with(claimant()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Claim received"));
    }

    // ------------------------------------------------------------------ C9, C10

    @Test
    void withdrawAndAppealMapTransitionsAndConflicts() throws Exception {
        when(claimIntakeService.withdrawClaim(eq(new ClaimId(CLAIM)), any(), any())).thenReturn("WITHDRAWN")
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
        when(policyService.listPoliciesOfHolder("cust-priya"))
                .thenThrow(new DomainException(ErrorCode.POLICY_SYSTEM_UNAVAILABLE, "temporarily unavailable"));

        mvc.perform(get("/claimant/v1/policies").with(claimant()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POLICY_SYSTEM_UNAVAILABLE"));
    }

    @Test
    void uploadReturnsPresignedTarget() throws Exception {
        UUID upload = UUID.randomUUID();
        when(documentUploadService.requestUploadUrl("cust-priya", "photo.jpg", "image/jpeg", 2048)).thenReturn(new DocumentUploadService.RequestedUpload(upload,
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
        when(informationRequestService.respondToInformationRequest(eq(new ClaimId(CLAIM)), eq(request), any(), anyString(), any())).thenReturn("UNDER_ASSESSMENT");
        when(informationRequestService.respondToInformationRequest(eq(new ClaimId(CLAIM)), eq(CLAIM), any(), any(), any()))
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
        when(policyService.listPoliciesOfHolder("cust-priya")).thenThrow(new IllegalStateException("db password=hunter2"));

        mvc.perform(get("/claimant/v1/policies").with(claimant()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
