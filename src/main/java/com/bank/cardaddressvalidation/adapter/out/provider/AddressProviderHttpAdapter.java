package com.bank.cardaddressvalidation.adapter.out.provider;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import com.bank.cardaddressvalidation.domain.port.AddressProvider;
import com.bank.cardaddressvalidation.domain.port.dto.ProviderLookup;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
class AddressProviderHttpAdapter implements AddressProvider {

    static final String CIRCUIT_BREAKER = "cepProvider";

    private final CepProviderClient client;
    private final ObjectMapper objectMapper;
    private final LookupMetrics metrics;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "failFast")
    public Optional<ProviderLookup> findByCep(String cep) {
        String rawPayload;
        try {
            rawPayload = metrics.recordProviderLatency(() -> client.fetch(cep));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ProviderUnavailableException(e);
        }
        ProviderAddressResponse response = parse(rawPayload);
        return Optional.of(new ProviderLookup(toAddress(cep, response), rawPayload));
    }

    // Keeps the port contract intact: an open circuit fails fast as the same
    // ProviderUnavailableException the core already handles (opaque 5xx).
    private Optional<ProviderLookup> failFast(String cep, Throwable cause) {
        if (cause instanceof ProviderUnavailableException e) {
            throw e;
        }
        throw new ProviderUnavailableException(cause);
    }

    private ProviderAddressResponse parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, ProviderAddressResponse.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ProviderUnavailableException(e);
        }
    }

    private Address toAddress(String cep, ProviderAddressResponse response) {
        return new Address(
                cep,
                response.logradouro(),
                response.complemento(),
                response.bairro(),
                response.cidade(),
                response.uf());
    }
}
