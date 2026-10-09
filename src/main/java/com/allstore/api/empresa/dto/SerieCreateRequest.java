package com.allstore.api.empresa.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param correlativoInicial último número ya emitido con esta serie (0 si es nueva). Sirve
 *                           para continuar la numeración al migrar desde otro sistema.
 */
public record SerieCreateRequest(
        @NotNull(message = "El tipo de comprobante es obligatorio")
        TipoComprobante tipoComprobante,
        @NotBlank(message = "La serie es obligatoria")
        String codigo,
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @PositiveOrZero(message = "El correlativo inicial no puede ser negativo")
        @Max(value = 99_999_999, message = "El correlativo no puede superar 99999999")
        Long correlativoInicial
) {
}
