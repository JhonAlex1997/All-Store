package com.allstore.api.producto.dto;

import java.math.BigDecimal;

/**
 * @param varianteId     null si es el costo del modelo
 * @param costo          el costo guardado (null = sin costo propio)
 * @param costoEfectivo  el que rige: el propio o, si no tiene, el del modelo
 */
public record CostoResponse(Long productoId, Long varianteId, String codigo, BigDecimal costo, BigDecimal costoEfectivo) {
}
