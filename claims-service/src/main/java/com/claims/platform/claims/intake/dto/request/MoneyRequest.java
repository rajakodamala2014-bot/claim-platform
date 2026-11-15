package com.claims.platform.claims.intake.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.common.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
