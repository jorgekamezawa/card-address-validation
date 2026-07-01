package com.bank.cardaddressvalidation.adapter.out.persistence;

import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Audit row; {@code responsePayload} is a verbatim {@code jsonb}, null when there was no body. */
@Entity
@Table(name = "cep_lookup_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CepLookupLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String cep;

    @Column(name = "queried_at", nullable = false)
    private Instant queriedAt;

    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Column(nullable = false, length = 20)
    private Source source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    public CepLookupLogJpaEntity(String cep, Instant queriedAt, Outcome outcome, Source source, String responsePayload) {
        this.cep = cep;
        this.queriedAt = queriedAt;
        this.outcome = outcome;
        this.source = source;
        this.responsePayload = responsePayload;
    }
}
