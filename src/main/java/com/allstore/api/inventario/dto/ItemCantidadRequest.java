package com.allstore.api.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemCantidadRequest(
        @NotNull(message = "La variante es obligatoria")
        Long varianteId,
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        @Max(value = 100_000, message = "La cantidad no puede superar 100000 unidades")
        Integer cantidad
) {
}
