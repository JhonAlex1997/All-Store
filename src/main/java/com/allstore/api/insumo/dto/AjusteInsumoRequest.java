package com.allstore.api.insumo.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.insumo.entity.MotivoInsumo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Inventario inicial o ajuste de insumos en un local. Motivos: INVENTARIO_INICIAL, AJUSTE_ENTRADA, AJUSTE_SALIDA. */
public record AjusteInsumoRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        @NotNull(message = "El motivo es obligatorio")
        MotivoInsumo motivo,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        @TextoSeguro
        String observacion,
        @NotEmpty(message = "Indica al menos un insumo")
        @Size(max = 200, message = "Máximo 200 insumos por vez")
        List<@Valid Item> items
) {
    public record Item(
            @NotNull(message = "El insumo es obligatorio")
            Long insumoId,
            @NotNull(message = "La cantidad es obligatoria")
            @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero")
            @Digits(integer = 9, fraction = 3, message = "La cantidad admite hasta 3 decimales")
            BigDecimal cantidad
    ) {
    }
}
