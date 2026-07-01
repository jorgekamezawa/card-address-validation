package com.bank.cardaddressvalidation.adapter.out.persistence;

import com.bank.cardaddressvalidation.domain.Source;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Stores {@link Source} as the lowercase token used by the Spec ({@code provider} / {@code cache}). */
@Converter(autoApply = true)
class SourceConverter implements AttributeConverter<Source, String> {

    @Override
    public String convertToDatabaseColumn(Source source) {
        return source == null ? null : source.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Source convertToEntityAttribute(String value) {
        return value == null ? null : Source.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
