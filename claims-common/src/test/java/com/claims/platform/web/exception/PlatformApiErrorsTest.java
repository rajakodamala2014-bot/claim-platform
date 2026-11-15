package com.claims.platform.web.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformApiErrorsTest {

    @Test
    void validationCarriesAStableCodeAndTheFieldErrors() {
        ProblemDetail problem = PlatformApiErrors.validationFailed(List.of(new Problems.FieldError("password", "must not be blank")));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(problem.getDetail()).isEqualTo("Request validation failed");
        assertThat(problem.getProperties().get("errors").toString()).contains("password");
    }

    @Test
    void unreadableBodyAndUnexpectedFailuresUseTheSharedWording() {
        assertThat(PlatformApiErrors.unreadableBody().getDetail()).isEqualTo(PlatformApiErrors.UNREADABLE_BODY);
        assertThat(PlatformApiErrors.unreadableBody().getProperties()).containsEntry("code", "VALIDATION_FAILED");

        ProblemDetail internal = PlatformApiErrors.internalError();
        assertThat(internal.getStatus()).isEqualTo(500);
        assertThat(internal.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(internal.getDetail()).contains("correlation id");
    }

    @Test
    void frameworkStatusesKeepTheHttpStatusAsTheCode() {
        ProblemDetail problem = (ProblemDetail) PlatformApiErrors.frameworkStatus(null, HttpStatus.METHOD_NOT_ALLOWED).getBody();

        assertThat(problem.getStatus()).isEqualTo(405);
        assertThat(problem.getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
        assertThat(problem.getDetail()).isEqualTo("Method Not Allowed");
    }
}
