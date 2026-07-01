package com.bank.cardaddressvalidation.domain;

/** {@code cep} is canonical (digits only); output formatting is applied at the HTTP edge. */
public record Address(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String cidade,
        String uf
) {
}
