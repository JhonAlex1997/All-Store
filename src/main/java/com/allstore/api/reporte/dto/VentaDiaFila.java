package com.allstore.api.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ventas de cada día.
 *
 * @param neto ventas - notas de crédito + notas de débito
 */
public record VentaDiaFila(
        LocalDate fecha,
        long comprobantes,
        long prendas,
        BigDecimal ventas,
        BigDecimal notasCredito,
        BigDecimal neto
) {
}
