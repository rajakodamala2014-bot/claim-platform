package com.claims.platform.web;

import com.claims.platform.common.error.ErrorCodes;
import com.claims.platform.common.error.ErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<ErrorDetail> details;

    public ApiException(HttpStatus status, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message, List.of());
    }

    public static ApiException badRequest(String code, String message) {
        return of(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException badRequest(String code, String message, List<ErrorDetail> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
    }

    public static ApiException unauthorized(String message) {
        return of(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return of(HttpStatus.FORBIDDEN, ErrorCodes.NOT_ALLOWED, message);
    }

    public static ApiException forbidden(String code, String message) {
        return of(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String message) {
        return of(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message);
    }

    public static ApiException conflict(String code, String message) {
        return of(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return of(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static ApiException unavailable(String code, String message) {
        return of(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
