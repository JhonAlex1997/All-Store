package com.allstore.api.venta.entity;

public enum EstadoComprobante {
    /** Vigente. */
    EMITIDO,
    /** Anulado: la mercadería volvió al stock. */
    ANULADO,
    /** Nota de venta que se formalizó en una boleta o factura (ver comprobanteOrigen de esta). */
    CONVERTIDO,
    /**
     * SUNAT lo rechazó y se emitió otro en su lugar (ver reemplazaA del nuevo). No tiene validez;
     * la mercadería y el cobro siguen siendo los suyos, ahora sustentados por el nuevo.
     */
    REEMPLAZADO
}
