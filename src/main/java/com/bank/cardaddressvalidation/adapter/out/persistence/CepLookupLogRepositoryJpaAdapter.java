package com.bank.cardaddressvalidation.adapter.out.persistence;

import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.Source;
import com.bank.cardaddressvalidation.domain.port.CepLookupLogRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CepLookupLogRepositoryJpaAdapter implements CepLookupLogRepository {

    private final CepLookupLogJpaRepository repository;

    @Override
    public void record(String cep, Instant queriedAt, Outcome outcome, Source source, String responsePayload) {
        repository.save(new CepLookupLogJpaEntity(cep, queriedAt, outcome, source, responsePayload));
    }
}
