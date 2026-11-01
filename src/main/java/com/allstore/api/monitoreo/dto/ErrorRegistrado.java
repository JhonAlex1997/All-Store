package com.allstore.api.monitoreo.dto;

import java.time.OffsetDateTime;

/**
 * Un error interno reciente.
 *
 * @param solicitud código X-Request-Id de la solicitud (para buscarla en el log); null en un proceso
 * @param origen    "POST /api/ventas" o "proceso envio-sunat"
 * @param tipo      clase de la excepción
 */
public record ErrorRegistrado(OffsetDateTime fecha, String solicitud, String origen, String tipo, String mensaje) {
}
