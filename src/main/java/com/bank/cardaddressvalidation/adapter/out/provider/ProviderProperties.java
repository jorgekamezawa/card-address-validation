package com.bank.cardaddressvalidation.adapter.out.provider;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "provider.cep")
record ProviderProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
