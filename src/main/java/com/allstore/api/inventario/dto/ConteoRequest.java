package com.allstore.api.inventario.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Abre un conteo en un local. Sin categoría ni productos se cuenta todo lo que el local ha tenido;
 * con ellos, todas las tallas y colores de esos modelos (también las que el sistema tiene en cero).
 */
public record ConteoRequest(
        @NotNull(message = "El establecimiento es obligatorio")
        Long establecimientoId,
        Long categoriaId,
        @Size(max = 200, message = "Máximo 200 modelos por conteo")
        List<Long> productoIds,
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        @TextoSeguro
        String observacion
) {
}
