package com.bank.cardaddressvalidation.domain.port;

import com.bank.cardaddressvalidation.domain.port.dto.ProviderLookup;
import java.util.Optional;

/**
 * Empty means the provider answered that the CEP does not exist; failures surface
 * as a {@link com.bank.cardaddressvalidation.domain.ProviderUnavailableException}.
 */
public interface AddressProvider {

    Optional<ProviderLookup> findByCep(String cep);
}
