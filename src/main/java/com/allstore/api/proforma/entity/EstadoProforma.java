package com.allstore.api.proforma.entity;

/** La vigencia no es un estado: una proforma PENDIENTE con la fecha pasada está vencida. */
public enum EstadoProforma {
    PENDIENTE,
    /** Se cobró: tiene la boleta, factura o nota de venta que la cerró. */
    VENDIDA,
    ANULADA
}
