package com.allstore.api.producto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TallaRequest(
        @NotBlank(message = "El código de la talla es obligatorio")
        @Size(max = 10, message = "El código de la talla no puede superar 10 caracteres")
        @Pattern(regexp = "[A-Za-z0-9]+", message = "La talla solo puede tener letras y números")
        String codigo,
        @NotNull(message = "El orden es obligatorio")
        @PositiveOrZero(message = "El orden no puede ser negativo")
        Integer orden,
        Boolean activo
) {
}
