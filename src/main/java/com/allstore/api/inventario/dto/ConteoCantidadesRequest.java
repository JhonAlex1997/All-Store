package com.allstore.api.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lo contado. Cada prenda se indica por su id o por el código escaneado (código de barras o SKU).
 *
 * @param modo REEMPLAZAR (por defecto): la cantidad es el total contado de la prenda. SUMAR: se suma a
 *             lo ya contado, para escanear prenda por prenda con cantidad 1
 */
public record ConteoCantidadesRequest(
        Modo modo,
        @NotEmpty(message = "Indica al menos una prenda")
        @Size(max = 500, message = "Máximo 500 prendas por vez")
        List<@Valid Item> items
) {
    public enum Modo { REEMPLAZAR, SUMAR }

    public record Item(
            Long varianteId,
            @Size(max = 40, message = "El código no puede superar 40 caracteres")
            String codigo,
            @NotNull(message = "La cantidad es obligatoria")
            @PositiveOrZero(message = "La cantidad no puede ser negativa")
            @Max(value = 100_000, message = "La cantidad no puede superar 100000")
            Integer cantidad
    ) {
    }
}
