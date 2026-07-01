package com.bank.cardaddressvalidation.domain.port;

import com.bank.cardaddressvalidation.domain.Outcome;
import com.bank.cardaddressvalidation.domain.Source;
import java.time.Instant;

/** Audit trail for every lookup that reached the cache or the provider. */
public interface CepLookupLogRepository {

    void record(String cep, Instant queriedAt, Outcome outcome, Source source, String responsePayload);
}
