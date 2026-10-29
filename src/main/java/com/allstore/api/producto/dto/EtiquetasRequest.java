package com.allstore.api.producto.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Etiquetas con código de barras para pegar en las prendas.
 *
 * @param formato ROLLO_50X25 (impresora térmica de etiquetas) o A4 (hoja de 24 etiquetas)
 * @param items   qué prendas y cuántas etiquetas de cada una
 */
public record EtiquetasRequest(
        @NotNull(message = "Indica el formato: ROLLO_50X25 o A4")
        Formato formato,
        @NotEmpty(message = "Indica al menos una prenda")
        @Size(max = 500, message = "Máximo 500 prendas distintas por impresión")
        List<@Valid Item> items
) {
    public enum Formato {
        ROLLO_50X25,
        A4
    }

    public record Item(
            @NotNull(message = "La prenda es obligatoria")
            Long varianteId,
            @Min(value = 1, message = "Al menos 1 etiqueta")
            @Max(value = 500, message = "Máximo 500 etiquetas por prenda")
            int cantidad
    ) {
    }
}
