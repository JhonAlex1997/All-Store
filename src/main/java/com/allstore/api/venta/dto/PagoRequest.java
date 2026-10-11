package com.allstore.api.venta.dto;

import com.allstore.api.venta.entity.MetodoPago;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param monto         lo que se paga con este método (la suma de los pagos debe ser el total)
 * @param montoRecibido solo efectivo: lo que entregó el cliente, para calcular el vuelto
 * @param referencia    número de operación de tarjeta, Yape, Plin o transferencia
 */
public record PagoRequest(
        @NotNull(message = "El método de pago es obligatorio")
        MetodoPago metodo,
        @NotNull(message = "El monto del pago es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto del pago debe ser mayor a cero")
        @Digits(integer = 10, fraction = 2, message = "El monto admite como máximo 2 decimales")
        BigDecimal monto,
        @Digits(integer = 10, fraction = 2, message = "El monto recibido admite como máximo 2 decimales")
        BigDecimal montoRecibido,
        @Size(max = 50, message = "La referencia no puede superar 50 caracteres")
        String referencia
) {
}
