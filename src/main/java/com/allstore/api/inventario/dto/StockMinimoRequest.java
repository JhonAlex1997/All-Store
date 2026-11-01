package com.allstore.api.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mínimos propios de varias prendas en un local. {@code minimo = null} quita el mínimo propio (vuelve a
 * regir el del modelo).
 */
public record StockMinimoRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @NotEmpty(message = "Indica al menos una prenda")
        @Size(max = 500, message = "Máximo 500 prendas por vez")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "La prenda es obligatoria")
            Long varianteId,
            @PositiveOrZero(message = "El mínimo no puede ser negativo")
            @Max(value = 100_000, message = "El mínimo no puede superar 100000")
            Integer minimo
    ) {
    }
}
