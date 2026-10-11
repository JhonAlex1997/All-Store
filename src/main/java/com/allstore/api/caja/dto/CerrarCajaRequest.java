package com.allstore.api.caja.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** @param montoContado efectivo que el cajero contó físicamente al cerrar */
public record CerrarCajaRequest(
        @NotNull(message = "Indica el efectivo contado")
        @DecimalMin(value = "0.00", message = "El efectivo contado no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal montoContado,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        String observacion
) {
}
