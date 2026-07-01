package com.bank.cardaddressvalidation.adapter.out.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import com.bank.cardaddressvalidation.domain.port.dto.ProviderLookup;
import com.bank.cardaddressvalidation.observability.LookupMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class AddressProviderHttpAdapterTest {

    private static final String FOUND_PAYLOAD = """
            {"cep":"01001-000","logradouro":"Praça da Sé","complemento":"lado ímpar",\
            "bairro":"Sé","cidade":"São Paulo","uf":"SP"}""";

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SimpleMeterRegistry registry;
    private AddressProviderHttpAdapter provider;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        ProviderProperties properties = new ProviderProperties(
                wireMock.baseUrl(), Duration.ofMillis(500), Duration.ofMillis(500));
        CepProviderClient client = new ProviderClientConfig().cepProviderClient(RestClient.builder(), properties);
        provider = new AddressProviderHttpAdapter(client, new ObjectMapper(), new LookupMetrics(registry));
    }

    @Test
    void returnsAddressAndRawPayloadWhenProviderFindsTheCep() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/01001000/json")).willReturn(okJson(FOUND_PAYLOAD)));

        Optional<ProviderLookup> result = provider.findByCep("01001000");

        assertThat(result).isPresent();
        assertThat(result.get().address()).isEqualTo(
                new Address("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP"));
        // raw payload kept verbatim: the provider's masked cep survives, proving it is the
        // raw response, not a re-serialization of the canonical Address (cep "01001000").
        assertThat(result.get().rawPayload()).contains("01001-000").contains("São Paulo");
        assertThat(registry.get("cep.provider.latency").timer().count()).isEqualTo(1L);
    }

    @Test
    void returnsEmptyWhenProviderSaysTheCepDoesNotExist() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/99999999/json")).willReturn(aResponse().withStatus(404)));

        assertThat(provider.findByCep("99999999")).isEmpty();
    }

    @Test
    void throwsProviderUnavailableWhenProviderReturnsServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/01001000/json")).willReturn(serverError()));

        assertThatThrownBy(() -> provider.findByCep("01001000"))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void throwsProviderUnavailableWhenProviderTimesOut() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/01001000/json"))
                .willReturn(okJson("{}").withFixedDelay(1500)));

        assertThatThrownBy(() -> provider.findByCep("01001000"))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void throwsProviderUnavailableWhenResponseIsMalformed() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/01001000/json")).willReturn(okJson("not-json")));

        assertThatThrownBy(() -> provider.findByCep("01001000"))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
