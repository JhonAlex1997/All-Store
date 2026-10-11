package com.allstore.api.caja.dto;

import com.allstore.api.venta.entity.MetodoPago;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cuadre de una caja.
 *
 * <pre>
 * efectivoEsperado = apertura + ventas en efectivo - devoluciones en efectivo + ingresos - egresos
 * </pre>
 *
 * @param ventasPorMetodo       cobrado por método (efectivo, tarjeta, Yape...): sirve para cuadrar
 *                              también el voucher de la tarjeta y los Yape del día
 * @param devolucionesPorMetodo dinero devuelto con notas de crédito, por método
 */
public record ResumenCaja(
        BigDecimal montoApertura,
        long cantidadVentas,
        BigDecimal totalVentas,
        Map<MetodoPago, BigDecimal> ventasPorMetodo,
        long cantidadNotasCredito,
        BigDecimal totalNotasCredito,
        Map<MetodoPago, BigDecimal> devolucionesPorMetodo,
        BigDecimal totalIngresos,
        BigDecimal totalEgresos,
        BigDecimal efectivoEsperado
) {
}
