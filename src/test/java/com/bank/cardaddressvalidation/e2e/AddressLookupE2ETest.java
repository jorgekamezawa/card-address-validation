package com.bank.cardaddressvalidation.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.cardaddressvalidation.TestcontainersConfiguration;
import com.bank.cardaddressvalidation.adapter.in.web.AddressResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Critical happy path, end to end through the running application:
 * request -> cache miss -> provider -> audit -> response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AddressLookupE2ETest {

    private static final String FOUND_PAYLOAD = """
            {"cep":"01001-000","logradouro":"Praça da Sé","complemento":"lado ímpar",\
            "bairro":"Sé","cidade":"São Paulo","uf":"SP"}""";

    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("provider.cep.base-url", wireMock::baseUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void looksUpFoundCepEndToEndAndWritesAuditRow() {
        wireMock.stubFor(get(urlPathEqualTo("/ws/01001000/json")).willReturn(okJson(FOUND_PAYLOAD)));

        ResponseEntity<AddressResponse> response =
                restTemplate.getForEntity("/addresses/01001000", AddressResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                new AddressResponse("01001-000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP"));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/ws/01001000/json")));

        Map<String, Object> auditRow = jdbcClient.sql("""
                        SELECT outcome, source, response_payload IS NOT NULL AS has_payload
                        FROM cep_lookup_log WHERE cep = :cep""")
                .param("cep", "01001000")
                .query()
                .singleRow();
        assertThat(auditRow.get("outcome")).isEqualTo("found");
        assertThat(auditRow.get("source")).isEqualTo("provider");
        assertThat(auditRow.get("has_payload")).isEqualTo(true);
    }
}
