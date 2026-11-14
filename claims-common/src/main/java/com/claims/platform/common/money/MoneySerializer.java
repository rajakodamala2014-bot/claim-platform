package com.claims.platform.common.money;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.RoundingMode;

public class MoneySerializer extends JsonSerializer<Money> {
    @Override
    public void serialize(Money value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("amount", value.amount().setScale(2, RoundingMode.HALF_UP));
        gen.writeStringField("currency", value.currency());
        gen.writeEndObject();
    }
}
