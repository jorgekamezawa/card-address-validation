package com.bank.cardaddressvalidation.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.cardaddressvalidation.application.AddressLookupService;
import com.bank.cardaddressvalidation.domain.Address;
import com.bank.cardaddressvalidation.domain.AddressNotFoundException;
import com.bank.cardaddressvalidation.domain.InvalidCepFormatException;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AddressController.class)
@Import(AddressResponseMapper.class)
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressLookupService addressLookupService;

    @Test
    void returns200WithFormattedCepWhenFound() throws Exception {
        when(addressLookupService.lookup("01001000")).thenReturn(
                new Address("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP"));

        mockMvc.perform(get("/addresses/01001000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("01001-000"))
                .andExpect(jsonPath("$.logradouro").value("Praça da Sé"))
                .andExpect(jsonPath("$.cidade").value("São Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"));
    }

    @Test
    void acceptsMaskedCepInThePathAndReturnsFormattedCep() throws Exception {
        when(addressLookupService.lookup("01001-000")).thenReturn(
                new Address("01001000", "Praça da Sé", "lado ímpar", "Sé", "São Paulo", "SP"));

        mockMvc.perform(get("/addresses/01001-000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cep").value("01001-000"));
    }

    @Test
    void returns422WhenFormatIsInvalid() throws Exception {
        when(addressLookupService.lookup("123")).thenThrow(new InvalidCepFormatException());

        mockMvc.perform(get("/addresses/123"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("INVALID_CEP_FORMAT"))
                .andExpect(jsonPath("$.message").value(containsString("inválido")))
                .andExpect(jsonPath("$.path").value("/addresses/123"));
    }

    @Test
    void returns404WhenNotFound() throws Exception {
        when(addressLookupService.lookup("99999999")).thenThrow(new AddressNotFoundException());

        mockMvc.perform(get("/addresses/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("CEP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("não localizado")));
    }

    @Test
    void returns500WithOpaqueBodyWhenUnexpectedErrorOccurs() throws Exception {
        when(addressLookupService.lookup("01001000")).thenThrow(
                new IllegalStateException("boom at jdbc://10.9.8.7"));

        mockMvc.perform(get("/addresses/01001000"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                // opacity: no cause, host, or stack leaks to the client
                .andExpect(content().string(not(containsString("boom"))))
                .andExpect(content().string(not(containsString("10.9.8.7"))));
    }

    @Test
    void returns503WithOpaqueBodyWhenProviderFails() throws Exception {
        when(addressLookupService.lookup("01001000")).thenThrow(
                new ProviderUnavailableException(new RuntimeException("ViaCEP down at 10.1.2.3")));

        mockMvc.perform(get("/addresses/01001000"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_UNAVAILABLE"))
                // opacity: no provider name, cause, host, or stack leaks to the client
                .andExpect(content().string(not(containsString("ViaCEP"))))
                .andExpect(content().string(not(containsString("10.1.2.3"))));
    }
}
