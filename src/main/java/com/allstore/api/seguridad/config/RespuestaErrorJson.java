package com.allstore.api.seguridad.config;

import com.allstore.api.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Escribe los errores de seguridad (401/403) con el mismo formato que el resto de la API. Hace
 * falta porque se producen en los filtros, antes de llegar al GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class RespuestaErrorJson {

    private final JsonMapper jsonMapper;

    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                         String mensaje) throws IOException {
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), status.value(), status.getReasonPhrase(),
                mensaje, request.getRequestURI(), null);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
