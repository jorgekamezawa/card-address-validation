package com.bank.cardaddressvalidation.domain.port.dto;

import com.bank.cardaddressvalidation.domain.Address;

/** Mapped {@link Address} plus the raw payload kept verbatim for the audit trail. */
public record ProviderLookup(Address address, String rawPayload) {
}
