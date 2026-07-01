package com.bank.cardaddressvalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.AddressNotFoundException;
import com.bank.cardaddressvalidation.domain.InvalidCepFormatException;
import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import com.bank.cardaddressvalidation.domain.Source;
import com.bank.cardaddressvalidation.domain.port.AddressCache;
import com.bank.cardaddressvalidation.domain.port.AddressProvider;
import com.bank.cardaddressvalidation.domain.port.CepLookupLogRepository;
import com.bank.cardaddressvalidation.domain.port.dto.ProviderLookup;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressLookupServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final Address ADDRESS =
            new Address("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP");
    private static final String RAW_PAYLOAD = "{\"cep\":\"01001-000\",\"cidade\":\"São Paulo\"}";

    @Mock
    private AddressProvider addressProvider;

    @Mock
    private AddressCache addressCache;

    @Mock
    private CepLookupLogRepository auditLog;

    private SimpleMeterRegistry registry;
    private AddressLookupService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AddressLookupService(
                addressProvider, addressCache, auditLog, new LookupMetrics(registry), clock);
    }

    private double outcomeCount(String outcome) {
        return registry.get("cep.lookups").tag("outcome", outcome).counter().count();
    }

    @Test
    void resolvesFromProviderOnCacheMissThenCachesAndAuditsProvider() {
        when(addressCache.get("01001000")).thenReturn(Optional.empty());
        when(addressProvider.findByCep("01001000")).thenReturn(Optional.of(new ProviderLookup(ADDRESS, RAW_PAYLOAD)));

        Address result = service.lookup("01001-000");

        assertThat(result).isEqualTo(ADDRESS);
        verify(addressCache).put("01001000", ADDRESS);
        verify(auditLog).record("01001000", NOW, Outcome.FOUND, Source.PROVIDER, RAW_PAYLOAD);
        assertThat(outcomeCount("found")).isEqualTo(1.0);
    }

    @Test
    void resolvesFromCacheWithoutCallingProvider() {
        when(addressCache.get("01001000")).thenReturn(Optional.of(ADDRESS));

        Address result = service.lookup("01001000");

        assertThat(result).isEqualTo(ADDRESS);
        verifyNoInteractions(addressProvider);
        verify(addressCache, never()).put(any(), any());
        verify(auditLog).record("01001000", NOW, Outcome.FOUND, Source.CACHE, null);
        assertThat(outcomeCount("found")).isEqualTo(1.0);
    }

    @Test
    void throwsNotFoundAndAuditsWhenProviderHasNoAddress() {
        when(addressCache.get("99999999")).thenReturn(Optional.empty());
        when(addressProvider.findByCep("99999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("99999999")).isInstanceOf(AddressNotFoundException.class);

        verify(auditLog).record("99999999", NOW, Outcome.NOT_FOUND, Source.PROVIDER, null);
        verify(addressCache, never()).put(any(), any());
        assertThat(outcomeCount("not_found")).isEqualTo(1.0);
    }

    @Test
    void auditsProviderErrorAndRethrowsOpaquely() {
        when(addressCache.get("01001000")).thenReturn(Optional.empty());
        when(addressProvider.findByCep("01001000"))
                .thenThrow(new ProviderUnavailableException(new RuntimeException("boom")));

        assertThatThrownBy(() -> service.lookup("01001000")).isInstanceOf(ProviderUnavailableException.class);

        verify(auditLog).record("01001000", NOW, Outcome.PROVIDER_ERROR, Source.PROVIDER, null);
        verify(addressCache, never()).put(any(), any());
        assertThat(outcomeCount("provider_error")).isEqualTo(1.0);
    }

    @Test
    void rejectsInvalidCepBeforeAnyIntegration() {
        assertThatThrownBy(() -> service.lookup("123")).isInstanceOf(InvalidCepFormatException.class);

        verifyNoInteractions(addressProvider, addressCache, auditLog);
        assertThat(outcomeCount("invalid_format")).isEqualTo(1.0);
    }
}
