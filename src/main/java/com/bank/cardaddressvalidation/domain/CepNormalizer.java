package com.bank.cardaddressvalidation.domain;

/** Strips non-digits then requires exactly 8, so masked and unmasked CEPs normalize alike. */
public final class CepNormalizer {

    private static final int CANONICAL_LENGTH = 8;
    private static final String NON_DIGITS = "\\D";

    private CepNormalizer() {
    }

    public static String normalize(String rawCep) {
        String digitsOnly = rawCep == null ? "" : rawCep.replaceAll(NON_DIGITS, "");
        if (digitsOnly.length() != CANONICAL_LENGTH) {
            throw new InvalidCepFormatException();
        }
        return digitsOnly;
    }
}
