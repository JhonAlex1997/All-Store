package com.allstore.api.promocion.dto;

import java.math.BigDecimal;

/**
 * Precio de una prenda hoy en un local, para mostrarlo en la caja al escanear.
 *
 * @param precioCampana null si ninguna campaña aplica
 * @param campana       la campaña con el mejor precio; null si ninguna
 */
public record PrecioCampanaResponse(Long varianteId, String sku, BigDecimal precioNormal, BigDecimal precioCampana,
                                    Long campanaId, String campana) {
}
