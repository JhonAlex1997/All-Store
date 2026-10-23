package com.allstore.api.venta.dto;

import com.allstore.api.venta.entity.MetodoPago;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Totales de las ventas que cumplen los filtros. Solo cuenta lo vigente: no los anulados, ni las
 * notas de venta ya convertidas, ni los rechazados por SUNAT ya reemitidos (cuenta el comprobante
 * que los reemplazó).
 *
 * @param totalVentas      notas de venta, boletas y facturas
 * @param totalNeto        ventas − notas de crédito + notas de débito
 * @param cobradoPorMetodo lo que entró por cada método en ventas y notas de débito. Es la cifra
 *                         exacta de "cuánto entró por Yape": una venta pagada mitad en efectivo y
 *                         mitad por Yape suma solo su parte en cada método
 * @param devueltoPorMetodo lo devuelto al cliente por cada método en las notas de crédito
 * @param cantidadAnulados anulados entre los filtrados (no suman en ningún total)
 */
public record TotalesVentasResponse(
        long cantidadVentas,
        BigDecimal totalVentas,
        long cantidadNotasCredito,
        BigDecimal totalNotasCredito,
        long cantidadNotasDebito,
        BigDecimal totalNotasDebito,
        BigDecimal totalNeto,
        Map<MetodoPago, BigDecimal> cobradoPorMetodo,
        Map<MetodoPago, BigDecimal> devueltoPorMetodo,
        long cantidadAnulados
) {
}
