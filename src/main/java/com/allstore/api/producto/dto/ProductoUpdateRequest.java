package com.allstore.api.producto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edición de un producto. El código no se edita porque forma parte del SKU de sus variantes. */
public record ProductoUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,
        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String descripcion,
        @NotNull(message = "La categoría es obligatoria")
        Long categoriaId,
        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite como máximo 2 decimales")
        BigDecimal precioVenta,
        @NotNull(message = "Indica si el producto está activo")
        Boolean activo
) {
}
