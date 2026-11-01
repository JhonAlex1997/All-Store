package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/**
 * Ventas netas de una combinación talla + color, con su stock actual (para reponer lo que se vende).
 */
public record VentaTallaColorFila(
        Long varianteId,
        String sku,
        String producto,
        String talla,
        String color,
        long unidades,
        BigDecimal total,
        long stockActual
) {
}
