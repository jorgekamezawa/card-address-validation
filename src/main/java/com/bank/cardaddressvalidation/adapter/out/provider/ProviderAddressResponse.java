package com.bank.cardaddressvalidation.adapter.out.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Only the fields the domain maps; unknown ones are ignored here but kept in the raw payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
record ProviderAddressResponse(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String cidade,
        String uf) {
}
