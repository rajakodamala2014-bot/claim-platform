package com.claims.platform.messaging.exception;

/** The message can never be processed (unparseable, wrong shape): goes to the DLQ without retrying. */
public class MalformedMessageException extends RuntimeException {
    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
