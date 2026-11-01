package com.allstore.api.reporte.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Prenda con stock que no se vende hace tiempo (o nunca): candidata a liquidación o a moverla de local.
 *
 * @param valorStock    stock x precio de venta
 * @param ultimaVenta   null si nunca se vendió
 * @param diasSinVender desde la última venta o, si nunca se vendió, desde que se registró
 */
public record SinRotacionFila(
        Long varianteId,
        String sku,
        String producto,
        String talla,
        String color,
        long stock,
        BigDecimal precio,
        BigDecimal valorStock,
        OffsetDateTime ultimaVenta,
        long diasSinVender
) {
}
