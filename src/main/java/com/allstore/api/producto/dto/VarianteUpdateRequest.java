package com.allstore.api.producto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code precioVenta} en null hace que la variante vuelva a usar el precio del producto. */
public record VarianteUpdateRequest(
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite como máximo 2 decimales")
        BigDecimal precioVenta,
        @NotNull(message = "Indica si la variante está activa")
        Boolean activo
) {
}
