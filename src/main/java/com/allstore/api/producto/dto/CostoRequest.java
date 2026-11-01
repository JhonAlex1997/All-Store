package com.allstore.api.producto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** Costo unitario SIN IGV; null lo quita (es opcional). */
public record CostoRequest(
        @DecimalMin(value = "0.00", message = "El costo no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El costo admite como máximo 2 decimales")
        BigDecimal costo
) {
}
