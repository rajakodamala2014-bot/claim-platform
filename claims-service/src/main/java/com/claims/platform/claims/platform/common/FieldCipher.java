package com.claims.platform.claims.platform.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts sensitive columns (bank account numbers) at rest with AES-256-GCM. Stored form:
 * {@code v1:<base64(iv || ciphertext+tag)>}. The row id is bound as associated data, so a ciphertext copied onto
 * another row does not decrypt. The {@code v1} prefix leaves room for key rotation (a future {@code v2} key decrypts
 * old rows while new writes use it); values without a prefix are rows written before encryption and are read as is.
 */
@Component
public class FieldCipher {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${claims.crypto.field-key:}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("claims.crypto.field-key must be base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("claims.crypto.field-key (FIELD_ENCRYPTION_KEY) must be a base64 256-bit key");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext, String rowId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(rowId.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("field encryption failed", ex);
        }
    }

    public String decrypt(String stored, String rowId) {
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] blob = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            cipher.updateAAD(rowId.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("field decryption failed (wrong key or tampered value)", ex);
        }
    }
}
