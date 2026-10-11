package com.allstore.api.venta.entity;

public enum EstadoComprobante {
    /** Vigente. */
    EMITIDO,
    /** Anulado: la mercadería volvió al stock. */
    ANULADO,
    /** Nota de venta que se formalizó en una boleta o factura (ver comprobanteOrigen de esta). */
    CONVERTIDO
}
