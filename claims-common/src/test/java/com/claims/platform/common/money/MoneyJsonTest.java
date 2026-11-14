package com.claims.platform.common.money;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyJsonTest {

    @Test
    void writesScaleAndReadsBack() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new Money(new BigDecimal("3700"), "sgd"));
        assertTrue(json.contains("\"amount\":3700.00"));
        assertTrue(json.contains("\"currency\":\"SGD\""));
        Money money = mapper.readValue(json, Money.class);
        assertEquals(new BigDecimal("3700.00"), money.amount().setScale(2));
        assertEquals("SGD", money.currency());
    }
}
