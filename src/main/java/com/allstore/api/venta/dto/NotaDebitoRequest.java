package com.allstore.api.venta.dto;

import com.allstore.api.venta.entity.MotivoNotaDebito;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Nota de débito sobre una boleta o factura: cobra un monto adicional al cliente.
 *
 * @param serieId   serie de nota de débito: empieza con B si modifica una boleta, con F si
 *                  modifica una factura
 * @param sustento  descripción del motivo (SUNAT la exige), ej. "Penalidad por pago fuera de plazo"
 * @param conceptos lo que se cobra
 * @param pagos     cómo paga el cliente; deben sumar el total de la nota
 */
public record NotaDebitoRequest(
        @NotNull(message = "La serie es obligatoria")
        Long serieId,
        @NotNull(message = "El motivo es obligatorio")
        MotivoNotaDebito motivo,
        @NotBlank(message = "Describe el motivo de la nota de débito")
        @Size(max = 250, message = "El sustento no puede superar 250 caracteres")
        String sustento,
        @NotEmpty(message = "Agrega al menos un concepto")
        @Size(max = 20, message = "Máximo 20 conceptos")
        List<@Valid ConceptoNotaDebitoRequest> conceptos,
        @NotEmpty(message = "Registra al menos un pago")
        @Size(max = 10, message = "Máximo 10 pagos")
        List<@Valid PagoRequest> pagos
) {
}
