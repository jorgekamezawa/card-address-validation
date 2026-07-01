package com.bank.cardaddressvalidation.adapter.out.provider;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Returns the raw body as String so the adapter can map it and keep it verbatim for the audit. */
@HttpExchange("/ws")
interface CepProviderClient {

    @GetExchange("/{cep}/json")
    String fetch(@PathVariable String cep);
}
