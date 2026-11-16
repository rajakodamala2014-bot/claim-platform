package com.claims.platform.claims.settlement.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.claims.platform.claims.settlement.dto.response.PaymentCallbackResponse;
import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.exception.ValidationException;
import com.claims.platform.claims.settlement.service.PaymentResultService;
import com.claims.platform.common.payments.PaymentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Fallback channel for payment results when the Payments system can't use Kafka:
 * {@code POST /internal/v1/payments/callback} with {@code X-Signature: sha256=<hex HMAC of the raw body>}.
 * Not routed by the gateway (internal network only) and authenticated by the signature, not a user token.
 * Feeds the same {@code PaymentResultService} as the {@code payments.payment-results} consumer.
 */
@RestController
@Tag(name = "Payment callbacks", description = "Fallback for the Payments system when it cannot use Kafka")
public class PaymentCallbackController {
    public static final String PATH = "/internal/v1/payments/callback";
    public static final String SIGNATURE_HEADER = "X-Signature";

    private final PaymentResultService paymentResultService;
    private final ObjectMapper json;
    private final byte[] secret;

    public PaymentCallbackController(PaymentResultService paymentResultService, ObjectMapper json,
                                     @Value("${claims.payments.webhook-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("claims.payments.webhook-secret must be at least 32 characters");
        }
        this.paymentResultService = paymentResultService;
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(PATH)
    @Operation(summary = "Record a payment result", description = "Signed with X-Signature: sha256=<hex HMAC-SHA256 of the raw body>. Idempotent: a result for an old instruction is ignored.")
    @SecurityRequirements // no bearer token: the request is authorised by its signature
    @ApiResponse(responseCode = "200", description = "APPLIED, IGNORED_DUPLICATE or UNKNOWN_CLAIM")
    @ApiResponse(responseCode = "400", description = "Body is not a payment result (VALIDATION_FAILED)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid signature (INVALID_SIGNATURE)")
    public PaymentCallbackResponse recordPaymentResult(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                                     @RequestBody String body) {
        if (signature == null || !MessageDigest.isEqual(
                ("sha256=" + sign(secret, body)).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new DomainException(ErrorCode.INVALID_SIGNATURE, "Missing or invalid " + SIGNATURE_HEADER);
        }
        PaymentResult result;
        try {
            result = json.readValue(body, PaymentResult.class);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException ex) {
            throw ValidationException.of("body", "is not a valid payment result");
        }
        return new PaymentCallbackResponse(paymentResultService.recordPaymentResult(result).name());
    }

    public static String sign(byte[] secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
