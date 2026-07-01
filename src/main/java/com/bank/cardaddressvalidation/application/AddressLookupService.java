package com.bank.cardaddressvalidation.application;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.AddressNotFoundException;
import com.bank.cardaddressvalidation.domain.CepNormalizer;
import com.bank.cardaddressvalidation.domain.InvalidCepFormatException;
import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import com.bank.cardaddressvalidation.domain.Source;
import com.bank.cardaddressvalidation.domain.port.AddressCache;
import com.bank.cardaddressvalidation.domain.port.AddressProvider;
import com.bank.cardaddressvalidation.domain.port.CepLookupLogRepository;
import com.bank.cardaddressvalidation.domain.port.dto.ProviderLookup;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cache-aside CEP lookup with audit. Not transactional on purpose: each audit row
 * must commit even when the lookup then fails (not-found, provider error).
 */
@Service
@RequiredArgsConstructor
public class AddressLookupService {

    private final AddressProvider addressProvider;
    private final AddressCache addressCache;
    private final CepLookupLogRepository auditLog;
    private final LookupMetrics metrics;
    private final Clock clock;

    public Address lookup(String rawCep) {
        String cep = normalize(rawCep);

        Optional<Address> cached = addressCache.get(cep);
        if (cached.isPresent()) {
            metrics.recordOutcome(Outcome.FOUND);
            audit(cep, Outcome.FOUND, Source.CACHE, null);
            return cached.get();
        }

        ProviderLookup lookup = callProvider(cep);
        addressCache.put(cep, lookup.address());
        metrics.recordOutcome(Outcome.FOUND);
        audit(cep, Outcome.FOUND, Source.PROVIDER, lookup.rawPayload());
        return lookup.address();
    }

    private String normalize(String rawCep) {
        try {
            return CepNormalizer.normalize(rawCep);
        } catch (InvalidCepFormatException e) {
            metrics.recordInvalidFormat();
            throw e;
        }
    }

    private ProviderLookup callProvider(String cep) {
        Optional<ProviderLookup> lookup;
        try {
            lookup = addressProvider.findByCep(cep);
        } catch (ProviderUnavailableException e) {
            metrics.recordOutcome(Outcome.PROVIDER_ERROR);
            audit(cep, Outcome.PROVIDER_ERROR, Source.PROVIDER, null);
            throw e;
        }
        if (lookup.isEmpty()) {
            metrics.recordOutcome(Outcome.NOT_FOUND);
            audit(cep, Outcome.NOT_FOUND, Source.PROVIDER, null);
            throw new AddressNotFoundException();
        }
        return lookup.get();
    }

    private void audit(String cep, Outcome outcome, Source source, String responsePayload) {
        auditLog.record(cep, Instant.now(clock), outcome, source, responsePayload);
    }
}
