package com.allstore.api.producto.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Agrega a un producto todas las combinaciones talla x color que aún no tenga. */
public record VariantesRequest(
        @NotEmpty(message = "Selecciona al menos una talla")
        List<Long> tallaIds,
        @NotEmpty(message = "Selecciona al menos un color")
        List<Long> colorIds
) {
}
