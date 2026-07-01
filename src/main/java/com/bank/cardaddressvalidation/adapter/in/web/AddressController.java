package com.bank.cardaddressvalidation.adapter.in.web;

import com.bank.cardaddressvalidation.application.AddressLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
class AddressController {

    private final AddressLookupService addressLookupService;
    private final AddressResponseMapper mapper;

    @GetMapping("/{cep}")
    AddressResponse getByCep(@PathVariable String cep) {
        return mapper.toResponse(addressLookupService.lookup(cep));
    }
}
