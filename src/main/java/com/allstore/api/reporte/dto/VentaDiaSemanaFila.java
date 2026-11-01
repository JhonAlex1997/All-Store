package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/** Ventas por día de la semana (1 = lunes). */
public record VentaDiaSemanaFila(
        int dia,
        String nombre,
        long comprobantes,
        BigDecimal total
) {
}
