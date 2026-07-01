package com.bank.cardaddressvalidation.adapter.in.web;

import com.bank.cardaddressvalidation.domain.AddressNotFoundException;
import com.bank.cardaddressvalidation.domain.InvalidCepFormatException;
import com.bank.cardaddressvalidation.domain.ProviderUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Explicit responses for client input errors; opaque for provider/internal failures (detail stays in logs). */
@RestControllerAdvice
@Slf4j
class ApiExceptionHandler {

    @ExceptionHandler(InvalidCepFormatException.class)
    ResponseEntity<ApiError> handleInvalidFormat(InvalidCepFormatException e, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CEP_FORMAT",
                "O CEP informado é inválido: deve conter 8 dígitos.", request);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(AddressNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CEP_NOT_FOUND", "CEP não localizado.", request);
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    ResponseEntity<ApiError> handleProviderUnavailable(ProviderUnavailableException e, HttpServletRequest request) {
        log.warn("CEP provider unavailable", e);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE",
                "Serviço temporariamente indisponível. Tente novamente mais tarde.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error handling request", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno. Tente novamente mais tarde.", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
