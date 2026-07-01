package com.bank.cardaddressvalidation.domain;

/** A valid CEP the provider does not know; maps to 404 at the edge. */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException() {
        super("CEP not found");
    }
}
