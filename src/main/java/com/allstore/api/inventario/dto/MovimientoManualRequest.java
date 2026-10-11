package com.allstore.api.inventario.dto;

import com.allstore.api.inventario.entity.MotivoMovimiento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Inventario inicial o ajuste tras un conteo físico. Solo se aceptan los motivos manuales
 * (INVENTARIO_INICIAL, AJUSTE_ENTRADA, AJUSTE_SALIDA); ventas y traslados tienen su propio flujo.
 */
public record MovimientoManualRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @NotNull(message = "El motivo es obligatorio")
        MotivoMovimiento motivo,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        String observacion,
        @NotEmpty(message = "Agrega al menos una prenda")
        @Size(max = 500, message = "Máximo 500 prendas por movimiento")
        List<@Valid ItemCantidadRequest> items
) {
}
