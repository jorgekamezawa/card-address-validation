package com.bank.cardaddressvalidation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.Source;
import com.bank.cardaddressvalidation.domain.port.CepLookupLogRepository;
import com.bank.cardaddressvalidation.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CepLookupLogPersistenceTest {

    private static final String PAYLOAD = """
            {"cep":"01001-000","logradouro":"Praça da Sé","complemento":"lado ímpar",\
            "bairro":"Sé","cidade":"São Paulo","uf":"SP"}""";

    @Autowired
    private CepLookupLogRepository store;

    @Autowired
    private CepLookupLogJpaRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void recordsAuditRowAndReadsTheJsonbPayloadBack() {
        store.record("01001000", Instant.parse("2026-06-30T12:00:00Z"), Outcome.FOUND, Source.PROVIDER, PAYLOAD);
        forceReloadFromDatabase();

        CepLookupLogJpaEntity row = repository.findAll().get(0);
        assertThat(row.getCep()).isEqualTo("01001000");
        assertThat(row.getQueriedAt()).isEqualTo(Instant.parse("2026-06-30T12:00:00Z"));
        assertThat(row.getOutcome()).isEqualTo(Outcome.FOUND);
        assertThat(row.getSource()).isEqualTo(Source.PROVIDER);
        // jsonb preserves content, not byte-exact formatting: the raw provider fields
        // survive the round-trip (incl. the masked cep, kept verbatim).
        assertThat(row.getResponsePayload())
                .contains("01001-000")
                .contains("São Paulo")
                .contains("Praça da Sé");
    }

    @Test
    void storesPayloadAsQueryableJsonbNotPlainText() {
        store.record("01001000", Instant.now(), Outcome.FOUND, Source.PROVIDER, PAYLOAD);
        entityManager.flush();

        String cidade = jdbcClient.sql("SELECT response_payload ->> 'cidade' FROM cep_lookup_log WHERE cep = ?")
                .param("01001000")
                .query(String.class)
                .single();
        assertThat(cidade).isEqualTo("São Paulo");
    }

    @Test
    void storesOutcomeAndSourceAsLowercaseTokens() {
        store.record("01001000", Instant.now(), Outcome.PROVIDER_ERROR, Source.PROVIDER, null);
        entityManager.flush();

        var row = jdbcClient.sql("SELECT outcome, source FROM cep_lookup_log WHERE cep = ?")
                .param("01001000")
                .query((rs, n) -> new String[] {rs.getString("outcome"), rs.getString("source")})
                .single();
        assertThat(row).containsExactly("provider_error", "provider");
    }

    @Test
    void keepsPayloadNullWhenProviderHadNoBody() {
        store.record("01001000", Instant.now(), Outcome.PROVIDER_ERROR, Source.PROVIDER, null);
        forceReloadFromDatabase();

        CepLookupLogJpaEntity row = repository.findAll().get(0);
        assertThat(row.getResponsePayload()).isNull();
        assertThat(row.getOutcome()).isEqualTo(Outcome.PROVIDER_ERROR);
    }

    private void forceReloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }
}
