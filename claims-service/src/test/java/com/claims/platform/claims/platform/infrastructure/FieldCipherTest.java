package com.claims.platform.claims.platform.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class FieldCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final FieldCipher cipher = new FieldCipher(KEY);

    @Test
    void roundTripsAndNeverStoresThePlaintext() {
        String stored = cipher.encrypt("123456789", "row-1");
        assertThat(stored).startsWith("v1:").doesNotContain("123456789");
        assertThat(cipher.encrypt("123456789", "row-1")).isNotEqualTo(stored); // fresh IV each time
        assertThat(cipher.decrypt(stored, "row-1")).isEqualTo("123456789");
    }

    @Test
    void ciphertextIsBoundToItsRowAndKey() {
        String stored = cipher.encrypt("123456789", "row-1");
        assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(stored, "row-2"));
        byte[] other = new byte[32];
        other[0] = 1;
        assertThatIllegalStateException().isThrownBy(() -> new FieldCipher(Base64.getEncoder().encodeToString(other))
                .decrypt(stored, "row-1"));
    }

    @Test
    void rowsWrittenBeforeEncryptionAreStillReadable() {
        assertThat(cipher.decrypt("123456789", "row-1")).isEqualTo("123456789");
    }

    @Test
    void refusesToStartWithoutA256BitKey() {
        assertThatIllegalStateException().isThrownBy(() -> new FieldCipher(""));
        assertThatIllegalStateException().isThrownBy(() -> new FieldCipher("not base64!"));
    }
}
