package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/**
 * Ventas netas de un modelo en el periodo (las devoluciones con nota de crédito restan).
 *
 * @param comprobantes  ventas en que aparece
 * @param participacion % del total vendido en el periodo
 */
public record VentaProductoFila(
        Long productoId,
        String codigo,
        String producto,
        String categoria,
        long unidades,
        long comprobantes,
        BigDecimal total,
        BigDecimal participacion
) {
}
