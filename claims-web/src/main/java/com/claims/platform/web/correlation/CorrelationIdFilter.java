package com.claims.platform.web.correlation;

import com.claims.platform.common.web.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Accepts the caller's {@code X-Correlation-Id} (or mints one), exposes it via MDC for logs and echoes it on
 * the response. Untrusted values that don't look like an id are replaced, to prevent log injection.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = sanitize(request.getHeader(Headers.CORRELATION_ID));
        MDC.put(Headers.CORRELATION_ID_MDC, id);
        response.setHeader(Headers.CORRELATION_ID, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Headers.CORRELATION_ID_MDC);
        }
    }

    static String sanitize(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    /** Correlation id of the current request/thread, if any. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(Headers.CORRELATION_ID_MDC));
    }
}
