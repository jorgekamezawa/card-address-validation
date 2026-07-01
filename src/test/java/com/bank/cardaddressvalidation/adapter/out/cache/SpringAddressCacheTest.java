package com.bank.cardaddressvalidation.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class SpringAddressCacheTest {

    private static final Address ADDRESS =
            new Address("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LookupMetrics metrics = new LookupMetrics(registry);

    @Test
    void returnsCachedValueAndCountsHit() {
        SpringAddressCache cache = new SpringAddressCache(
                new ConcurrentMapCacheManager(SpringAddressCache.CACHE_NAME), metrics);
        cache.put("01001000", ADDRESS);

        assertThat(cache.get("01001000")).contains(ADDRESS);
        assertThat(cacheCount("hit")).isEqualTo(1.0);
    }

    @Test
    void returnsEmptyAndCountsMissOnAbsentKey() {
        SpringAddressCache cache = new SpringAddressCache(
                new ConcurrentMapCacheManager(SpringAddressCache.CACHE_NAME), metrics);

        assertThat(cache.get("00000000")).isEmpty();
        assertThat(cacheCount("miss")).isEqualTo(1.0);
    }

    @Test
    void cacheOutageIsTreatedAsMissAndDoesNotBreakLookup() {
        CacheManager failing = mock(CacheManager.class);
        when(failing.getCache(any())).thenThrow(new RuntimeException("redis down"));
        SpringAddressCache cache = new SpringAddressCache(failing, metrics);

        assertThat(cache.get("01001000")).isEmpty();
        assertThat(cacheCount("miss")).isEqualTo(1.0);
    }

    @Test
    void absentCacheIsTreatedAsMissAndPutIsNoOp() {
        CacheManager noCache = mock(CacheManager.class);
        when(noCache.getCache(any())).thenReturn(null);
        SpringAddressCache cache = new SpringAddressCache(noCache, metrics);

        assertThat(cache.get("01001000")).isEmpty();
        cache.put("01001000", ADDRESS); // no cache present -> no-op, no exception
        assertThat(cacheCount("miss")).isEqualTo(1.0);
    }

    private double cacheCount(String result) {
        return registry.get("cep.cache").tag("result", result).counter().count();
    }
}
