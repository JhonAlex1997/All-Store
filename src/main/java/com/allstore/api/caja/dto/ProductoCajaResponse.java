package com.allstore.api.caja.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un modelo para vender en la caja con sus tallas y colores, el stock en el local de la caja y el precio
 * de campaña vigente. Los precios son de referencia para la pantalla: el que vale es el que calcula la venta.
 */
public record ProductoCajaResponse(
        Long productoId,
        String codigo,
        String nombre,
        String categoria,
        List<Variante> variantes
) {
    /**
     * @param precioCampana precio con la mejor campaña vigente (sobre el precio normal), o null si no hay
     * @param stock         unidades en el local de la caja
     */
    public record Variante(
            Long varianteId,
            String sku,
            String codigoBarras,
            String talla,
            String color,
            BigDecimal precio,
            BigDecimal precioPorMayor,
            BigDecimal precioCampana,
            String campana,
            int stock
    ) {
    }
}
