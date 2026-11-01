package com.allstore.api.producto.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

/** Mínimo de cada talla y color del modelo en cada tienda; null = sin mínimo. */
public record StockMinimoModeloRequest(
        @PositiveOrZero(message = "El mínimo no puede ser negativo")
        @Max(value = 100_000, message = "El mínimo no puede superar 100000")
        Integer stockMinimo
) {
}
