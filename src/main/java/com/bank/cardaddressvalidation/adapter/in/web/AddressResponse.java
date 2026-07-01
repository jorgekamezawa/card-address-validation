package com.bank.cardaddressvalidation.adapter.in.web;

public record AddressResponse(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String cidade,
        String uf) {
}
