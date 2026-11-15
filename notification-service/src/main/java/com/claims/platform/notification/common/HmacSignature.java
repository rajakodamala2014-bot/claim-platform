package com.claims.platform.notification.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 signatures of provider callbacks: {@code X-Signature: sha256=<hex HMAC of the raw body>}. */
public final class HmacSignature {
    public static final String HEADER = "X-Signature";
    private static final String PREFIX = "sha256=";

    private HmacSignature() {
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

    /** Constant-time check of a received header value against the expected signature of {@code body}. */
    public static boolean matches(byte[] secret, String body, String headerValue) {
        return headerValue != null && MessageDigest.isEqual(
                (PREFIX + sign(secret, body)).getBytes(StandardCharsets.UTF_8),
                headerValue.getBytes(StandardCharsets.UTF_8));
    }
}
