package com.allstore.api.empresa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Tipo y código de la serie no se editan. El correlativo solo puede subir (por ejemplo, para
 * saltar números ya usados en otro sistema); bajarlo repetiría números ante SUNAT.
 */
public record SerieUpdateRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @PositiveOrZero(message = "El correlativo no puede ser negativo")
        @Max(value = 99_999_999, message = "El correlativo no puede superar 99999999")
        Long correlativoActual,
        @NotNull(message = "Indica si la serie está activa")
        Boolean activo
) {
}
