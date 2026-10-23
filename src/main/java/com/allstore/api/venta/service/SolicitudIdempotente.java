package com.allstore.api.venta.service;

/**
 * Solicitud que emite un comprobante, identificada por la clave Idempotency-Key que genera la caja
 * (un UUID por venta, el mismo en cada reintento) y la huella de lo que pidió. Ver
 * {@link EmisionComprobante#yaAtendida}.
 *
 * @param clave  null si la caja no envió la cabecera (se emite siempre)
 * @param huella SHA-256 en hexadecimal de la operación y su contenido
 */
public record SolicitudIdempotente(String clave, String huella) {

    public static final String CABECERA = "Idempotency-Key";
    public static final SolicitudIdempotente NINGUNA = new SolicitudIdempotente(null, null);

    public boolean presente() {
        return clave != null;
    }
}
