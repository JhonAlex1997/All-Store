package com.allstore.api.venta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Una prenda vendida. El precio NO viene del cliente: se toma del producto en el servidor,
 * para que nadie pueda alterarlo desde el navegador. Solo se acepta un descuento, que queda
 * registrado con el usuario que hizo la venta.
 *
 * @param descuento descuento total de la línea, con IGV (opcional)
 */
public record VentaItemRequest(
        @NotNull(message = "La prenda es obligatoria")
        Long varianteId,
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        @Max(value = 1000, message = "La cantidad no puede superar 1000 unidades por línea")
        Integer cantidad,
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El descuento admite como máximo 2 decimales")
        BigDecimal descuento
) {
}
