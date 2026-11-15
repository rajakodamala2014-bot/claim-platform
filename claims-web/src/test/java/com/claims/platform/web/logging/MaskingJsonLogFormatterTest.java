package com.claims.platform.web.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingJsonLogFormatterTest {
    private final MaskingJsonLogFormatter formatter = new MaskingJsonLogFormatter();
    private final LoggerContext context = new LoggerContext();

    @Test
    void writesOneMaskedJsonObjectPerLineIncludingCorrelationId() throws Exception {
        LoggingEvent event = new LoggingEvent("fqcn", context.getLogger("claims.intake"), Level.INFO,
                "FNOL received from {}", null, new Object[]{"priya@example.com"});
        event.setMDCPropertyMap(Map.of("correlationId", "corr-123"));

        String line = formatter.format(event);

        assertThat(line).endsWith("\n").doesNotContain("priya@example.com");
        JsonNode json = new ObjectMapper().readTree(line);
        assertThat(json.get("message").asText()).isEqualTo("FNOL received from p***@example.com");
        assertThat(json.get("correlationId").asText()).isEqualTo("corr-123");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("logger").asText()).isEqualTo("claims.intake");
    }

    @Test
    void masksExceptionMessages() throws Exception {
        LoggingEvent event = new LoggingEvent("fqcn", context.getLogger("x"), Level.ERROR, "failed",
                new IllegalStateException("no policy for priya@example.com"), null);
        event.setMDCPropertyMap(Map.of());

        JsonNode json = new ObjectMapper().readTree(formatter.format(event));

        assertThat(json.at("/error/type").asText()).isEqualTo(IllegalStateException.class.getName());
        assertThat(json.at("/error/message").asText()).isEqualTo("no policy for p***@example.com");
        assertThat(json.at("/error/stack").asText()).doesNotContain("priya@example.com");
    }
}
