package com.claims.platform.claims.settlement.api;

import com.claims.platform.claims.core.domain.DomainException;
import com.claims.platform.claims.core.domain.ErrorCode;
import com.claims.platform.claims.core.domain.ValidationException;
import com.claims.platform.claims.settlement.application.RecordPaymentResult;
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
 * Feeds the same use case as the {@code payments.payment-results} consumer.
 */
@RestController
public class PaymentsWebhookController {
    public static final String PATH = "/internal/v1/payments/callback";
    public static final String SIGNATURE_HEADER = "X-Signature";

    private final RecordPaymentResult recordPaymentResult;
    private final ObjectMapper json;
    private final byte[] secret;

    public PaymentsWebhookController(RecordPaymentResult recordPaymentResult, ObjectMapper json,
                                     @Value("${claims.payments.webhook-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("claims.payments.webhook-secret must be at least 32 characters");
        }
        this.recordPaymentResult = recordPaymentResult;
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public record CallbackResponse(String outcome) {
    }

    @PostMapping(PATH)
    public CallbackResponse callback(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
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
        return new CallbackResponse(recordPaymentResult.record(result).name());
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
