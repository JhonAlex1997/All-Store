package com.allstore.api.caja.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** @param montoApertura fondo de sencillo con el que empieza el turno (puede ser 0) */
public record AbrirCajaRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @NotNull(message = "El monto de apertura es obligatorio")
        @DecimalMin(value = "0.00", message = "El monto de apertura no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal montoApertura
) {
}
