package com.claims.platform.gateway;

import com.claims.platform.web.problem.Problems;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerResponse;

/** Errors produced by the gateway itself, in the same problem+json shape the services use. */
final class GatewayProblems {
    static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    static final String RATE_LIMITED = "RATE_LIMITED";
    static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";

    private GatewayProblems() {
    }

    static ServerResponse.BodyBuilder status(HttpStatus status) {
        return ServerResponse.status(status).contentType(Problems.PROBLEM_JSON)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    static ServerResponse of(HttpStatus status, String code, String detail) {
        return status(status).body(Problems.of(status, code, detail));
    }
}
