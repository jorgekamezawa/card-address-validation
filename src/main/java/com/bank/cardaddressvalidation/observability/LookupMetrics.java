package com.bank.cardaddressvalidation.observability;

import com.bank.cardaddressvalidation.domain.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LookupMetrics {

    private static final String LOOKUPS = "cep.lookups";
    private static final String CACHE = "cep.cache";
    private static final String PROVIDER_LATENCY = "cep.provider.latency";

    private final MeterRegistry registry;

    public void recordOutcome(Outcome outcome) {
        countLookup(outcome.name().toLowerCase(Locale.ROOT));
    }

    public void recordInvalidFormat() {
        countLookup("invalid_format");
    }

    public void recordCacheHit() {
        registry.counter(CACHE, "result", "hit").increment();
    }

    public void recordCacheMiss() {
        registry.counter(CACHE, "result", "miss").increment();
    }

    public <T> T recordProviderLatency(Supplier<T> providerCall) {
        return registry.timer(PROVIDER_LATENCY).record(providerCall);
    }

    private void countLookup(String outcome) {
        registry.counter(LOOKUPS, "outcome", outcome).increment();
    }
}
