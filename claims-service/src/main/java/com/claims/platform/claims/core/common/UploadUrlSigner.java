package com.claims.platform.claims.core.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * HMAC-SHA256 signatures for pre-signed upload URLs. The signature covers upload id, expiry, content type and
 * maximum size, so none of them can be altered by the client.
 */
public class UploadUrlSigner {
    private final byte[] key;

    public UploadUrlSigner(String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(UUID uploadId, long expiresEpochSeconds, String contentType, long maxBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String payload = uploadId + "|" + expiresEpochSeconds + "|" + contentType + "|" + maxBytes;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    public boolean verify(UUID uploadId, long expiresEpochSeconds, String contentType, long maxBytes, String signature,
                          Instant now) {
        if (signature == null || now.getEpochSecond() > expiresEpochSeconds) {
            return false;
        }
        byte[] expected = sign(uploadId, expiresEpochSeconds, contentType, maxBytes).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}
