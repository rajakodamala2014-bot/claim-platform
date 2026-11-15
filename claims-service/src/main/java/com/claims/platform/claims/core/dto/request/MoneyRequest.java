package com.claims.platform.claims.core.dto.request;

import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.common.money.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Decimal amount as text plus an ISO currency. Shared by intake, handling and settlement so the wire format stays one contract. */
@Schema(description = "An amount of money: decimal amount as text and ISO 4217 currency")
public record MoneyRequest(@NotBlank String amount, @NotBlank @Size(min = 3, max = 3) String currency) {
    public Money toMoney(String field) {
        try {
            return Money.of(amount, currency);
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of(field, ex.getMessage());
        }
    }
}
