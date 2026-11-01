package com.allstore.api.produccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cierra la orden: cuántas prendas de cada talla y color entran al stock.
 *
 * @param actualizarCostos true: el costo calculado pasa a ser el costo de esas prendas (el costo es
 *                         manual: solo cambia si se pide)
 */
public record TerminarOrdenRequest(
        @NotEmpty(message = "Indica cuántas prendas salieron de cada talla y color")
        @Size(max = 200, message = "Máximo 200 tallas y colores")
        List<@Valid Cantidad> tallas,
        boolean actualizarCostos
) {
    public record Cantidad(
            @NotNull(message = "La prenda es obligatoria")
            Long varianteId,
            @NotNull(message = "La cantidad es obligatoria")
            @PositiveOrZero(message = "La cantidad no puede ser negativa")
            @Max(value = 100_000, message = "La cantidad no puede superar 100000")
            Integer cantidad
    ) {
    }
}
