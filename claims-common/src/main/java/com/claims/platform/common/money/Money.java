package com.claims.platform.common.money;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@JsonSerialize(using = MoneySerializer.class)
@JsonDeserialize(using = MoneyDeserializer.class)
public record Money(
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
    public Money {
        if (amount != null) {
            amount = amount.stripTrailingZeros();
        }
        if (currency != null) {
            currency = currency.trim().toUpperCase();
        }
    }

    public Money scaled() {
        return new Money(amount.setScale(2, java.math.RoundingMode.HALF_UP), currency);
    }
}
