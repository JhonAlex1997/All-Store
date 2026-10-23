package com.allstore.api.common.idempotencia;

/**
 * Solicitud que crea algo (un comprobante, un movimiento de caja, una guía, un ajuste o traslado de
 * inventario), identificada por la clave Idempotency-Key que genera el cliente (un UUID por
 * operación, el mismo en cada reintento) y la huella de lo que pidió. Ver {@link Idempotencia}.
 *
 * @param clave  null si el cliente no envió la cabecera (la operación se ejecuta siempre)
 * @param huella SHA-256 en hexadecimal de la operación y su contenido
 */
public record SolicitudIdempotente(String clave, String huella) {

    public static final String CABECERA = "Idempotency-Key";
    public static final SolicitudIdempotente NINGUNA = new SolicitudIdempotente(null, null);

    public boolean presente() {
        return clave != null;
    }
}
