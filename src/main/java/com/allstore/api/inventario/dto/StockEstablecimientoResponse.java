package com.allstore.api.inventario.dto;

import com.allstore.api.inventario.entity.Stock;

/** Stock de una variante en un establecimiento (para ver en qué local hay una prenda). */
public record StockEstablecimientoResponse(Long establecimientoId, String codigo, String establecimiento, int cantidad) {

    public static StockEstablecimientoResponse from(Stock s) {
        return new StockEstablecimientoResponse(s.getEstablecimiento().getId(), s.getEstablecimiento().getCodigo(),
                s.getEstablecimiento().getNombre(), s.getCantidad());
    }
}
