package com.bank.cardaddressvalidation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CepNormalizerTest {

    @Test
    void stripsMaskFromCepAndReturnsCanonicalDigits() {
        assertThat(CepNormalizer.normalize("01001-000")).isEqualTo("01001000");
    }

    @Test
    void keepsAlreadyCanonicalCepUnchanged() {
        assertThat(CepNormalizer.normalize("01001000")).isEqualTo("01001000");
    }

    @Test
    void stripsSurroundingWhitespaceAndAnyNonDigit() {
        assertThat(CepNormalizer.normalize(" 01001.000 ")).isEqualTo("01001000");
    }

    @ParameterizedTest(name = "rejects [{0}]")
    @NullSource
    @ValueSource(strings = {
            "",          // empty
            "   ",       // blank
            "-",         // mask only
            "123",       // too short
            "0100100",   // 7 digits
            "010010000", // 9 digits
            "01001-0000",// 9 digits once unmasked
            "abcde-fgh"  // letters, no digits
    })
    void rejectsCepThatIsNotExactlyEightDigits(String rawCep) {
        assertThatThrownBy(() -> CepNormalizer.normalize(rawCep))
                .isInstanceOf(InvalidCepFormatException.class);
    }
}
