package com.bank.cardaddressvalidation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.Source;
import org.junit.jupiter.api.Test;

class EnumConvertersTest {

    private final OutcomeConverter outcomeConverter = new OutcomeConverter();
    private final SourceConverter sourceConverter = new SourceConverter();

    @Test
    void outcomeConverterMapsBothDirectionsAndToleratesNull() {
        assertThat(outcomeConverter.convertToDatabaseColumn(Outcome.PROVIDER_ERROR)).isEqualTo("provider_error");
        assertThat(outcomeConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(outcomeConverter.convertToEntityAttribute("found")).isEqualTo(Outcome.FOUND);
        assertThat(outcomeConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void sourceConverterMapsBothDirectionsAndToleratesNull() {
        assertThat(sourceConverter.convertToDatabaseColumn(Source.CACHE)).isEqualTo("cache");
        assertThat(sourceConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(sourceConverter.convertToEntityAttribute("provider")).isEqualTo(Source.PROVIDER);
        assertThat(sourceConverter.convertToEntityAttribute(null)).isNull();
    }
}
