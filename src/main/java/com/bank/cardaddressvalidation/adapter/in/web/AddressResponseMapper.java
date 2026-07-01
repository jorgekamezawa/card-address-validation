package com.bank.cardaddressvalidation.adapter.in.web;

import com.bank.cardaddressvalidation.domain.Address;
import org.springframework.stereotype.Component;

@Component
class AddressResponseMapper {

    AddressResponse toResponse(Address address) {
        return new AddressResponse(
                formatCep(address.cep()),
                address.logradouro(),
                address.complemento(),
                address.bairro(),
                address.cidade(),
                address.uf());
    }

    private String formatCep(String canonicalCep) {
        return canonicalCep.substring(0, 5) + "-" + canonicalCep.substring(5);
    }
}
