package com.bank.cardaddressvalidation.adapter.out.cache;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.port.AddressCache;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SpringAddressCache implements AddressCache {

    static final String CACHE_NAME = "cepAddress";

    private final CacheManager cacheManager;
    private final LookupMetrics metrics;

    @Override
    public Optional<Address> get(String cep) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            Address address = cache == null ? null : cache.get(cep, Address.class);
            if (address != null) {
                metrics.recordCacheHit();
                return Optional.of(address);
            }
            metrics.recordCacheMiss();
            return Optional.empty();
        } catch (RuntimeException e) {
            metrics.recordCacheMiss();
            return Optional.empty();
        }
    }

    @Override
    public void put(String cep, Address address) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.put(cep, address);
            }
        } catch (RuntimeException e) {
            // cache is an optimization, not the source of truth: ignore put failures
        }
    }
}
