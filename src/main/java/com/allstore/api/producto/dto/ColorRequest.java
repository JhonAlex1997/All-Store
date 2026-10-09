package com.allstore.api.producto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ColorRequest(
        @NotBlank(message = "El nombre del color es obligatorio")
        @Size(max = 50, message = "El nombre del color no puede superar 50 caracteres")
        String nombre,
        @NotBlank(message = "La abreviatura del color es obligatoria")
        @Size(max = 10, message = "La abreviatura no puede superar 10 caracteres")
        @Pattern(regexp = "[A-Za-z0-9]+", message = "La abreviatura solo puede tener letras y números")
        String codigo,
        Boolean activo
) {
}
