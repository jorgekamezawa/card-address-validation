package com.bank.cardaddressvalidation.adapter.out.persistence;

import com.bank.cardaddressvalidation.domain.Outcome;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Stores {@link Outcome} as the lowercase token used by the Spec (e.g. {@code provider_error}). */
@Converter(autoApply = true)
class OutcomeConverter implements AttributeConverter<Outcome, String> {

    @Override
    public String convertToDatabaseColumn(Outcome outcome) {
        return outcome == null ? null : outcome.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Outcome convertToEntityAttribute(String value) {
        return value == null ? null : Outcome.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
