package com.bank.cardaddressvalidation.adapter.in.web;

import java.time.Instant;

/** {@code error} is a stable machine-readable code; {@code message} is human-facing. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}
