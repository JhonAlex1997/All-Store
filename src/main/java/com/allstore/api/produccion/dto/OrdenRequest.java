package com.allstore.api.produccion.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Nueva orden de producción de un modelo.
 *
 * @param establecimientoInsumosId de dónde salen la tela y los avíos
 * @param establecimientoDestinoId adonde entran las prendas terminadas
 * @param tallas                   cuántas de cada talla y color (prendas del modelo)
 */
public record OrdenRequest(
        @NotNull(message = "El modelo es obligatorio")
        Long productoId,
        @NotNull(message = "El local de los insumos es obligatorio")
        Long establecimientoInsumosId,
        @NotNull(message = "El local de destino es obligatorio")
        Long establecimientoDestinoId,
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        @TextoSeguro
        String observacion,
        @NotEmpty(message = "Indica al menos una talla y color")
        @Size(max = 200, message = "Máximo 200 tallas y colores")
        List<@Valid Cantidad> tallas
) {
    public record Cantidad(
            @NotNull(message = "La prenda es obligatoria")
            Long varianteId,
            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "La cantidad debe ser mayor a cero")
            @Max(value = 100_000, message = "La cantidad no puede superar 100000")
            Integer cantidad
    ) {
    }
}
