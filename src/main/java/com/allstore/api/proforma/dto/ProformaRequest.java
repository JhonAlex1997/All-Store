package com.allstore.api.proforma.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.venta.dto.VentaItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Proforma nueva o editada. Los precios no vienen del cliente: se calculan en el servidor igual que
 * en una venta (por mayor, campañas vigentes) y se respetan hasta el fin de la vigencia.
 *
 * @param establecimientoId local que cotiza (sus campañas; ahí se cobrará)
 * @param clienteId         vacío = "Clientes varios"
 * @param porMayor          marcarlo da el precio por mayor aunque no se llegue al mínimo de prendas
 */
public record ProformaRequest(
        @NotNull(message = "Indica el local")
        Long establecimientoId,
        Long clienteId,
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        @TextoSeguro
        String observacion,
        @NotEmpty(message = "Agrega al menos una prenda")
        @Size(max = 200, message = "Máximo 200 líneas por proforma")
        List<@Valid VentaItemRequest> items,
        Boolean porMayor
) {
}
