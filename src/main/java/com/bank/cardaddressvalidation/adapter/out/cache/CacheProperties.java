package com.bank.cardaddressvalidation.adapter.out.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache.cep")
record CacheProperties(Duration ttl) {
}
