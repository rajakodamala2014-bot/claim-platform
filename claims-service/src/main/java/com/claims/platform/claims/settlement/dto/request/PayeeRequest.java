package com.claims.platform.claims.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.entity.Payee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Bank details of the payee; the account number is encrypted at rest and masked in responses")
public record PayeeRequest(@NotBlank @Size(max = 200) String name,
                           @NotBlank @Size(max = 20) String bankCode,
                           @NotBlank @Pattern(regexp = "[0-9A-Za-z-]{4,40}") String accountNumber) {
    public Payee toPayee() {
        return new Payee(name, bankCode, accountNumber);
    }
}
