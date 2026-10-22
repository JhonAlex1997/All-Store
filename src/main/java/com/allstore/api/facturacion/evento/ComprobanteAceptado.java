package com.allstore.api.facturacion.evento;

/**
 * SUNAT aceptó un comprobante (con o sin observaciones). Se publica dentro de la transacción que
 * guarda la respuesta; quien lo escuche (ej. el envío por correo al cliente) no debe hacer trabajo
 * lento ni fallar, solo registrar lo que hará después.
 */
public record ComprobanteAceptado(Long comprobanteId) {
}
