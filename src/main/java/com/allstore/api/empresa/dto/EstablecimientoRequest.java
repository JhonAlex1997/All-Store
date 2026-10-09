package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.TipoEstablecimiento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EstablecimientoRequest(
        @NotBlank(message = "El código de establecimiento es obligatorio")
        @Pattern(regexp = "[0-9]{4}", message = "El código de establecimiento SUNAT tiene 4 dígitos (0000 = domicilio fiscal)")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,
        @NotNull(message = "El tipo de establecimiento es obligatorio")
        TipoEstablecimiento tipo,
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 250, message = "La dirección no puede superar 250 caracteres")
        String direccion,
        @NotBlank(message = "El ubigeo es obligatorio")
        @Pattern(regexp = "[0-9]{6}", message = "El ubigeo debe tener 6 dígitos")
        String ubigeo,
        Boolean activo
) {
}
