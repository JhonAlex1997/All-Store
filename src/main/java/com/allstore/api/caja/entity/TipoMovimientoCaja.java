package com.allstore.api.caja.entity;

public enum TipoMovimientoCaja {
    /** Entra efectivo que no es venta (ej. sencillo adicional). */
    INGRESO,
    /** Sale efectivo que no es devolución (ej. pago de un taxi o de un servicio). */
    EGRESO
}
