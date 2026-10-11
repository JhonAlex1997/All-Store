package com.allstore.api.venta.dto;

import com.allstore.api.venta.entity.MetodoPago;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Nota de crédito sobre una boleta o factura.
 *
 * @param serieId         serie de nota de crédito: empieza con B si modifica una boleta, con F si
 *                        modifica una factura
 * @param sustento        descripción del motivo (SUNAT la exige), ej. "Cambio de talla"
 * @param metodoReembolso cómo se devuelve el dinero; si es efectivo, sale de la caja
 * @param items           solo para DEVOLUCION_POR_ITEM: prendas y cantidades devueltas
 */
public record NotaCreditoRequest(
        @NotNull(message = "La serie es obligatoria")
        Long serieId,
        @NotNull(message = "El motivo es obligatorio")
        MotivoNotaCredito motivo,
        @NotBlank(message = "Describe el motivo de la nota de crédito")
        @Size(max = 250, message = "El sustento no puede superar 250 caracteres")
        String sustento,
        @NotNull(message = "Indica cómo se devuelve el dinero")
        MetodoPago metodoReembolso,
        @Size(max = 50, message = "La referencia no puede superar 50 caracteres")
        String referenciaReembolso,
        @Size(max = 200, message = "Máximo 200 líneas")
        List<@Valid ItemDevolucionRequest> items
) {
}
