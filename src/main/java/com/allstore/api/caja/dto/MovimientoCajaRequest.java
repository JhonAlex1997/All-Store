package com.allstore.api.caja.dto;

import com.allstore.api.caja.entity.TipoMovimientoCaja;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MovimientoCajaRequest(
        @NotNull(message = "Indica si es INGRESO o EGRESO")
        TipoMovimientoCaja tipo,
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal monto,
        @NotBlank(message = "Indica el concepto (ej. pago de taxi)")
        @Size(max = 250, message = "El concepto no puede superar 250 caracteres")
        String concepto
) {
}
