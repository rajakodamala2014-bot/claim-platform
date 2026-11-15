package com.claims.platform.gateway;

import com.claims.platform.common.web.Headers;
import com.claims.platform.web.common.CorrelationIdFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/** Request/response rewriting done at the edge. */
final class EdgeHeaders {
    private EdgeHeaders() {
    }

    /**
     * Upstream request: carries the correlation id minted (or sanitised) by {@link CorrelationIdFilter}, and
     * {@code X-Forwarded-*} written by the gateway. Values sent by the caller are discarded, because this is
     * the edge and nothing in front of it is trusted.
     */
    static HandlerFilterFunction<ServerResponse, ServerResponse> forwardRequestHeaders() {
        return (request, next) -> {
            var servletRequest = request.servletRequest();
            String host = request.headers().firstHeader(HttpHeaders.HOST);
            ServerRequest rewritten = ServerRequest.from(request).headers(headers -> {
                CorrelationIdFilter.current().ifPresent(id -> headers.set(Headers.CORRELATION_ID, id));
                headers.remove("Forwarded");
                headers.remove("X-Forwarded-Port");
                headers.remove("X-Forwarded-Prefix");
                headers.set("X-Forwarded-For", servletRequest.getRemoteAddr());
                headers.set("X-Forwarded-Proto", servletRequest.getScheme());
                if (host != null) {
                    headers.set("X-Forwarded-Host", host);
                }
            }).build();
            return next.handle(rewritten);
        };
    }

    /** Rejects requests that declare a body larger than {@code max} before they reach a service. */
    static HandlerFilterFunction<ServerResponse, ServerResponse> maxBodySize(DataSize max) {
        return (request, next) -> {
            long declared = request.servletRequest().getContentLengthLong();
            if (declared > max.toBytes()) {
                return GatewayProblems.of(HttpStatus.PAYLOAD_TOO_LARGE, GatewayProblems.PAYLOAD_TOO_LARGE,
                        "Request body is larger than " + max.toMegabytes() + " MB");
            }
            return next.handle(request);
        };
    }

    /**
     * Proxied API responses: never cached by browsers or intermediaries, never content-sniffed. The upstream's
     * own {@code X-Correlation-Id} is dropped because {@link CorrelationIdFilter} already put it on the response.
     * Must sit directly around the proxy: responses the gateway builds itself have read-only headers (and get
     * the same headers from {@link GatewayProblems}).
     */
    static HandlerFilterFunction<ServerResponse, ServerResponse> apiResponseHeaders() {
        return (request, next) -> {
            ServerResponse response = next.handle(request);
            HttpHeaders headers = response.headers();
            headers.remove(Headers.CORRELATION_ID);
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            return response;
        };
    }
}
