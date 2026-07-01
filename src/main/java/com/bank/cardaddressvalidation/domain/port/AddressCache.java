package com.bank.cardaddressvalidation.domain.port;

import com.bank.cardaddressvalidation.domain.Address;
import java.util.Optional;

/** Cache is an optimization, not the truth: a failing get is a miss and a failing put is ignored. */
public interface AddressCache {

    Optional<Address> get(String cep);

    void put(String cep, Address address);
}
