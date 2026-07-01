package com.bank.cardaddressvalidation.domain;

/** Provider down, timeout, error, or malformed response; maps to an opaque 5xx, cause kept for logs. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(Throwable cause) {
        super("CEP provider is unavailable", cause);
    }
}
