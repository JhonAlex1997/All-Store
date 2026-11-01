package com.allstore.api.insumo.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** @param unidadMedida catálogo 03 SUNAT: MTR, NIU, KGM, CEN... */
public record InsumoRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede superar 30 caracteres")
        @Pattern(regexp = "[A-Za-z0-9-]+", message = "El código solo admite letras, números y guiones")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        @TextoSeguro
        String nombre,
        @NotBlank(message = "La unidad de medida es obligatoria")
        @Pattern(regexp = "[A-Z0-9]{2,3}", message = "La unidad de medida es un código SUNAT (MTR, NIU, KGM...)")
        String unidadMedida,
        @DecimalMin(value = "0", message = "El costo no puede ser negativo")
        @Digits(integer = 8, fraction = 4, message = "El costo admite hasta 4 decimales")
        BigDecimal costo,
        Boolean activo
) {
}
