package com.claims.platform.web.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Claimant priya.nair@example.com submitted   | Claimant p***@example.com submitted",
            "call +65 91234567 now                       | call ****67 now",
            "call +6591234567 now                        | call ****67 now",
            "card 4111111111111111 used                  | card ****11 used",
            "phone 91234567                              | phone ****67",
    })
    void masksPersonalData(String input, String expected) {
        assertThat(PiiMasker.mask(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "claim SG-MTR-2026-000451 assigned",
            "claim 3f6c2a4e-1b7d-4c55-9a43-0e2f8d9b1c77 updated",
            "reserve set to 1250000 minor units",
            "retrying in 2000 ms",
    })
    void leavesNonPersonalIdentifiersAlone(String input) {
        assertThat(PiiMasker.mask(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void toleratesEmptyInput(String input) {
        assertThat(PiiMasker.mask(input)).isEqualTo(input);
    }
}
