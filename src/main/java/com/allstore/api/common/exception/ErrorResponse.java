package com.allstore.api.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Formato único de error de la API. {@code message} es siempre legible por el usuario final;
 * {@code details} lleva el error por campo cuando falla una validación.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> details
) {
}
