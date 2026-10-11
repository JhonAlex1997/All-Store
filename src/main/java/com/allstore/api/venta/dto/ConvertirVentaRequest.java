package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import jakarta.validation.constraints.NotNull;

/**
 * Formaliza una nota de venta en boleta o factura.
 *
 * @param tipoComprobante BOLETA o FACTURA
 * @param clienteId       vacío = el mismo cliente de la nota de venta (para factura se necesita uno con RUC)
 */
public record ConvertirVentaRequest(
        @NotNull(message = "Indica si se emite BOLETA o FACTURA")
        TipoComprobante tipoComprobante,
        @NotNull(message = "La serie es obligatoria")
        Long serieId,
        Long clienteId
) {
}
