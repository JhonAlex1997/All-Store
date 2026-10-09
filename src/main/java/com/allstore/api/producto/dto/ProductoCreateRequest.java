package com.allstore.api.producto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Alta de un producto. Si se envían tallas y colores se crean de una vez todas sus
 * combinaciones como variantes (ej. 2 colores x 5 tallas = 10 variantes).
 */
public record ProductoCreateRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede superar 30 caracteres")
        @Pattern(regexp = "[A-Za-z0-9-]+", message = "El código solo puede tener letras, números y guiones")
        String codigo,
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
        List<Long> tallaIds,
        List<Long> colorIds
) {
}
