package com.allstore.api.reporte.dto;

import java.math.BigDecimal;

/**
 * Lo que movió una campaña de promoción en el periodo.
 *
 * @param descuento lo que se dejó de cobrar por la campaña
 * @param total     lo cobrado por esas prendas (ya con el descuento)
 */
public record VentaCampanaFila(
        Long campanaId,
        String campana,
        long comprobantes,
        long unidades,
        BigDecimal descuento,
        BigDecimal total
) {
}
