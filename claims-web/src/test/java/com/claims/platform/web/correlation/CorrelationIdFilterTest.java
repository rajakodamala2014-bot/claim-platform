package com.claims.platform.web.correlation;

import com.claims.platform.common.web.Headers;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesCallerIdToMdcAndResponseThenClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Headers.CORRELATION_ID, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain.set(MDC.get(Headers.CORRELATION_ID_MDC));
            }
        });

        assertThat(seenInChain).hasValue("abc-123");
        assertThat(response.getHeader(Headers.CORRELATION_ID)).isEqualTo("abc-123");
        assertThat(MDC.get(Headers.CORRELATION_ID_MDC)).isNull();
    }

    @Test
    void mintsIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(Headers.CORRELATION_ID)).matches("[0-9a-f-]{36}");
    }

    @Test
    void replacesUnsafeIdToPreventLogInjection() {
        assertThat(CorrelationIdFilter.sanitize("evil\n{\"level\":\"ERROR\"}")).matches("[0-9a-f-]{36}");
        assertThat(CorrelationIdFilter.sanitize("x".repeat(65))).matches("[0-9a-f-]{36}");
        assertThat(CorrelationIdFilter.sanitize("ok_id.1-2")).isEqualTo("ok_id.1-2");
    }
}
