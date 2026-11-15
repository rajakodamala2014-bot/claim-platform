package com.claims.platform.claims.core.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.claims.platform.common.money.Money;

@Schema(description = "An amount of money: major units as text, minor units as a number, ISO 4217 currency")
public record MoneyResponse(String amount, long amountMinor, String currency) {
    public static MoneyResponse of(Money money) {
        return money == null ? null : new MoneyResponse(money.toMajor().toPlainString(), money.amountMinor(), money.currency());
    }
}
