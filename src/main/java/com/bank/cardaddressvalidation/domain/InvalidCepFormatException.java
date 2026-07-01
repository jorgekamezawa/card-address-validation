package com.bank.cardaddressvalidation.domain;

/** Barred before any integration: no provider call and no audit row is written. */
public class InvalidCepFormatException extends RuntimeException {

    public InvalidCepFormatException() {
        super("CEP must contain exactly 8 digits");
    }
}
