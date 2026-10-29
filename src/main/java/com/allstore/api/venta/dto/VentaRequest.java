package com.allstore.api.venta.dto;

import com.allstore.api.common.validacion.TextoSeguro;
import com.allstore.api.empresa.entity.TipoComprobante;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Nueva venta. El establecimiento (de dónde sale el stock) es el de la serie elegida.
 *
 * @param tipoComprobante NOTA_VENTA, BOLETA o FACTURA
 * @param clienteId       vacío = "Clientes varios"
 * @param porMayor        marcarlo da el precio por mayor aunque no se llegue al mínimo de prendas;
 *                        sin marcar, va por mayor solo si se llega al mínimo
 */
public record VentaRequest(
        @NotNull(message = "El tipo de comprobante es obligatorio")
        TipoComprobante tipoComprobante,
        @NotNull(message = "La serie es obligatoria")
        Long serieId,
        Long clienteId,
        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        @TextoSeguro
        String observacion,
        @NotEmpty(message = "Agrega al menos una prenda")
        @Size(max = 200, message = "Máximo 200 líneas por venta")
        List<@Valid VentaItemRequest> items,
        @NotEmpty(message = "Registra al menos un pago")
        @Size(max = 10, message = "Máximo 10 pagos por venta")
        List<@Valid PagoRequest> pagos,
        Boolean porMayor,
        @Size(max = 20, message = "El celular no puede superar 20 caracteres")
        @Pattern(regexp = "[0-9+ ()-]*", message = "El celular solo puede tener números, espacios, +, - y paréntesis")
        String whatsapp,
        @Email(message = "El correo no es válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String correo
) {
}
