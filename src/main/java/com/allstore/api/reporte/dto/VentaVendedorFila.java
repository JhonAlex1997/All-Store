package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/**
 * Lo que vendió cada usuario. La boleta o factura de una nota de venta convertida, o la reemisión de
 * un rechazado, cuenta para quien hizo la venta original.
 *
 * @param comprobantes   ventas (notas de venta, boletas y facturas)
 * @param prendas        unidades vendidas menos devueltas
 * @param neto           ventas - notas de crédito + notas de débito
 * @param ticketPromedio ventas / comprobantes
 * @param porMayorManual ventas en que dio precio por mayor sin el mínimo de prendas
 */
public record VentaVendedorFila(
        String vendedor,
        long comprobantes,
        long prendas,
        BigDecimal ventas,
        BigDecimal notasCredito,
        BigDecimal notasDebito,
        BigDecimal neto,
        BigDecimal ticketPromedio,
        BigDecimal descuentos,
        long porMayorManual
) {
}
