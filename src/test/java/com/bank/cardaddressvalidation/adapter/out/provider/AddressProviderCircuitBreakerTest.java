package com.bank.cardaddressvalidation.adapter.out.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import com.bank.cardaddressvalidation.domain.port.AddressProvider;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = AddressProviderCircuitBreakerTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AddressProviderCircuitBreakerTest {

    private static final String ANY_CEP_PATH = "/ws/.*/json";

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("provider.cep.base-url", wireMock::baseUrl);
    }

    @Autowired
    private AddressProvider provider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetState() {
        circuitBreakers.circuitBreaker(AddressProviderHttpAdapter.CIRCUIT_BREAKER).reset();
        wireMock.resetAll();
    }

    @Test
    void opensAfterRepeatedFailuresThenFailsFastWithoutCallingProvider() {
        wireMock.stubFor(get(urlPathMatching(ANY_CEP_PATH)).willReturn(serverError()));

        // Test profile: window=3, min-calls=3, threshold=50% -> 3 failures trip the circuit.
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> provider.findByCep("01001000"))
                    .isInstanceOf(ProviderUnavailableException.class);
        }
        assertThat(circuitBreakers.circuitBreaker(AddressProviderHttpAdapter.CIRCUIT_BREAKER).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        wireMock.resetRequests();
        // Open circuit still fails as ProviderUnavailableException (opaque 5xx), but fails fast...
        assertThatThrownBy(() -> provider.findByCep("01001000"))
                .isInstanceOf(ProviderUnavailableException.class);
        // ...without any HTTP call reaching the provider.
        wireMock.verify(0, getRequestedFor(urlPathMatching(ANY_CEP_PATH)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            CacheAutoConfiguration.class})
    @EnableConfigurationProperties(ProviderProperties.class)
    @Import({ProviderClientConfig.class, AddressProviderHttpAdapter.class})
    static class TestApp {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        LookupMetrics lookupMetrics(MeterRegistry registry) {
            return new LookupMetrics(registry);
        }
    }
}
