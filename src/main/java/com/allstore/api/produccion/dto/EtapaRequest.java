package com.allstore.api.produccion.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Iniciar o terminar una etapa.
 *
 * @param proveedorId   al iniciar: taller externo (null = taller propio)
 * @param unidades      al terminar: prendas que salieron buenas (las que faltan son merma)
 * @param costoServicio al terminar: lo que cobra el taller por la etapa (opcional)
 */
public record EtapaRequest(
        Long proveedorId,
        @PositiveOrZero(message = "Las unidades no pueden ser negativas")
        @Max(value = 100_000, message = "Las unidades no pueden superar 100000")
        Integer unidades,
        @DecimalMin(value = "0", message = "El costo no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El costo admite 2 decimales")
        BigDecimal costoServicio,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        @TextoSeguro
        String observacion
) {
}
