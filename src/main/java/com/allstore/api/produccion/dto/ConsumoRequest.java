package com.allstore.api.produccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Insumos que la orden consume (o devuelve) del local de insumos. */
public record ConsumoRequest(
        @NotEmpty(message = "Indica al menos un insumo")
        @Size(max = 100, message = "Máximo 100 insumos")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "El insumo es obligatorio")
            Long insumoId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero")
            @Digits(integer = 9, fraction = 3, message = "La cantidad admite hasta 3 decimales")
            BigDecimal cantidad
    ) {
    }
}
