package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/** Ventas por hora del día (hora de Lima): para saber cuándo reforzar la caja. */
public record VentaHoraFila(
        int hora,
        String rango,
        long comprobantes,
        BigDecimal total
) {
}
