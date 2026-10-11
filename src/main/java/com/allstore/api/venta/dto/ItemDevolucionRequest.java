package com.allstore.api.venta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Prenda y cantidad que el cliente devuelve. */
public record ItemDevolucionRequest(
        @NotNull(message = "La prenda es obligatoria")
        Long varianteId,
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Integer cantidad
) {
}
