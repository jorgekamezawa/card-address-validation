package com.bank.cardaddressvalidation.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.cardaddressvalidation.domain.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LookupMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LookupMetrics metrics = new LookupMetrics(registry);

    @Test
    void countsLookupsByOutcomeIncludingInvalidFormat() {
        metrics.recordOutcome(Outcome.FOUND);
        metrics.recordOutcome(Outcome.NOT_FOUND);
        metrics.recordOutcome(Outcome.PROVIDER_ERROR);
        metrics.recordInvalidFormat();

        assertThat(outcomeCount("found")).isEqualTo(1.0);
        assertThat(outcomeCount("not_found")).isEqualTo(1.0);
        assertThat(outcomeCount("provider_error")).isEqualTo(1.0);
        assertThat(outcomeCount("invalid_format")).isEqualTo(1.0);
    }

    @Test
    void countsCacheHitAndMiss() {
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        metrics.recordCacheMiss();

        assertThat(registry.get("cep.cache").tag("result", "hit").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("cep.cache").tag("result", "miss").counter().count()).isEqualTo(2.0);
    }

    @Test
    void timesProviderCallAndReturnsItsValue() {
        String result = metrics.recordProviderLatency(() -> "payload");

        assertThat(result).isEqualTo("payload");
        assertThat(registry.get("cep.provider.latency").timer().count()).isEqualTo(1L);
    }

    private double outcomeCount(String outcome) {
        return registry.get("cep.lookups").tag("outcome", outcome).counter().count();
    }
}
