package com.claims.platform.web.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

import java.time.Instant;
import java.util.Map;

/**
 * One JSON object per line, with PII masked in the message, MDC values and stack traces.
 * Enabled per service with {@code logging.structured.format.console=com.claims.platform.web.logging.MaskingJsonLogFormatter}.
 * MDC carries {@code correlationId}, so every line can be joined to a request and to the events it produced.
 */
public class MaskingJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String format(ILoggingEvent event) {
        ObjectNode json = mapper.createObjectNode();
        json.put("@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        json.put("level", event.getLevel().toString());
        json.put("logger", event.getLoggerName());
        json.put("thread", event.getThreadName());
        json.put("message", PiiMasker.mask(event.getFormattedMessage()));
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            mdc.forEach((key, value) -> json.put(key, PiiMasker.mask(value)));
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            ObjectNode error = json.putObject("error");
            error.put("type", throwable.getClassName());
            error.put("message", PiiMasker.mask(throwable.getMessage()));
            error.put("stack", PiiMasker.mask(ThrowableProxyUtil.asString(throwable)));
        }
        try {
            return mapper.writeValueAsString(json) + "\n";
        } catch (JsonProcessingException ex) {
            return "{\"level\":\"ERROR\",\"message\":\"log formatting failed\"}\n";
        }
    }
}
