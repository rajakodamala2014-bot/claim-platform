package com.claims.platform.claims.settlement.api;

import com.claims.platform.claims.core.application.ClaimantViews.MoneyView;
import com.claims.platform.claims.core.application.StaffClaims.Outcome;
import com.claims.platform.claims.core.domain.ClaimId;
import com.claims.platform.claims.core.domain.Payee;
import com.claims.platform.claims.core.domain.Settlement;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.platform.api.StaffRequest;
import com.claims.platform.claims.platform.api.StaffRequest.CommandResponse;
import com.claims.platform.claims.settlement.application.InstructSettlement;
import com.claims.platform.claims.settlement.application.RetrySettlement;
import com.claims.platform.common.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** S11 instruct payment, S12 retry a failed payment. If-Match required. */
@RestController
@RequestMapping("/staff/v1/claims/{claimId}/settlements")
public class StaffSettlementController {
    private final InstructSettlement instructSettlement;
    private final RetrySettlement retrySettlement;

    public StaffSettlementController(InstructSettlement instructSettlement, RetrySettlement retrySettlement) {
        this.instructSettlement = instructSettlement;
        this.retrySettlement = retrySettlement;
    }

    public record PayeeRequest(@NotBlank @Size(max = 200) String name,
                               @NotBlank @Size(max = 20) String bankCode,
                               @NotBlank @Pattern(regexp = "[0-9A-Za-z-]{4,40}") String accountNumber) {
        Payee toPayee() {
            return new Payee(name, bankCode, accountNumber);
        }
    }

    public record AmountRequest(@NotBlank String amount, @NotBlank @Size(min = 3, max = 3) String currency) {
    }

    public record SettlementRequestBody(@NotNull @Valid PayeeRequest payee, @NotNull @Valid AmountRequest amount) {
    }

    public record RetryRequest(@Valid PayeeRequest payee) {
    }

    public record SettlementResult(UUID settlementId, UUID instructionId, int attempt, MoneyView amount, String paymentStatus) {
        static SettlementResult of(Settlement s) {
            return new SettlementResult(s.id(), s.instructionId(), s.attempts(), MoneyView.of(s.amount()), s.status().name());
        }
    }

    @PostMapping
    public ResponseEntity<CommandResponse<SettlementResult>> instruct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                      @Valid @RequestBody SettlementRequestBody body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        Money amount;
        try {
            amount = Money.of(body.amount().amount(), body.amount().currency());
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of("amount", ex.getMessage());
        }
        Outcome<Settlement> outcome = instructSettlement.instruct(new ClaimId(claimId), StaffRequest.staff(jwt), version,
                body.payee().toPayee(), amount);
        return StaffRequest.respond(HttpStatus.CREATED, map(outcome));
    }

    @PostMapping("/{settlementId}/retry")
    public ResponseEntity<CommandResponse<SettlementResult>> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
                                                                   @PathVariable UUID settlementId,
                                                                   @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                   @Valid @RequestBody(required = false) RetryRequest body) {
        long version = StaffRequest.expectedVersion(ifMatch);
        Payee corrected = body == null || body.payee() == null ? null : body.payee().toPayee();
        return StaffRequest.respond(map(retrySettlement.retry(new ClaimId(claimId), settlementId, StaffRequest.staff(jwt),
                version, corrected)));
    }

    private static Outcome<SettlementResult> map(Outcome<Settlement> outcome) {
        return new Outcome<>(SettlementResult.of(outcome.value()), outcome.claimId(), outcome.status(), outcome.version());
    }
}
