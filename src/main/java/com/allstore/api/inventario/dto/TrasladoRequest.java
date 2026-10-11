package com.allstore.api.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Mueve prendas de un establecimiento a otro (ej. del almacén a la tienda) en una sola operación. */
public record TrasladoRequest(
        @NotNull(message = "El establecimiento de origen es obligatorio")
        Long origenId,
        @NotNull(message = "El establecimiento de destino es obligatorio")
        Long destinoId,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        String observacion,
        @NotEmpty(message = "Agrega al menos una prenda")
        @Size(max = 500, message = "Máximo 500 prendas por traslado")
        List<@Valid ItemCantidadRequest> items
) {
}
