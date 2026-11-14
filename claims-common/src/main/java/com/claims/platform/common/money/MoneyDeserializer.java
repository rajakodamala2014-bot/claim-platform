package com.claims.platform.common.money;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;

public class MoneyDeserializer extends JsonDeserializer<Money> {
    @Override
    public Money deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode amount = node.get("amount");
        JsonNode currency = node.get("currency");
        if (amount == null || amount.isNull() || currency == null || currency.isNull()) {
            return new Money(amount == null || amount.isNull() ? null : amount.decimalValue(),
                    currency == null || currency.isNull() ? null : currency.asText());
        }
        return new Money(new BigDecimal(amount.asText()), currency.asText());
    }
}
