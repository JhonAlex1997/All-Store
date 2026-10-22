package com.allstore.api.guia.dto;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.inventario.dto.ItemCantidadRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Nueva guía de remisión. El punto de partida es el local de la serie elegida.
 *
 * @param motivo                   VENTA o TRASLADO_ESTABLECIMIENTOS
 * @param fechaTraslado            inicio del traslado; vacío = hoy (no puede ser anterior a hoy)
 * @param comprobanteId            VENTA: la boleta o factura que se entrega
 * @param llegada                  VENTA: dirección de entrega al cliente
 * @param establecimientoDestinoId TRASLADO_ESTABLECIMIENTOS: local que recibe
 * @param items                    VENTA: vacío = todo lo vendido; TRASLADO: prendas y cantidades
 * @param transportista            transporte PUBLICO: la empresa de transporte
 * @param vehiculoM1l              transporte PRIVADO en auto o moto (categoría M1 o L): no se
 *                                 exigen conductor ni placa
 * @param conductor                transporte PRIVADO: quien maneja
 * @param placa                    transporte PRIVADO: placa del vehículo
 */
public record GuiaRemisionRequest(
        @NotNull(message = "La serie es obligatoria")
        Long serieId,
        @NotNull(message = "El motivo del traslado es obligatorio")
        MotivoTraslado motivo,
        LocalDate fechaTraslado,
        Long comprobanteId,
        @Valid Direccion llegada,
        Long establecimientoDestinoId,
        @NotNull(message = "El peso bruto es obligatorio")
        @DecimalMin(value = "0.001", message = "El peso bruto debe ser mayor a cero")
        @Digits(integer = 9, fraction = 3, message = "El peso admite como máximo 3 decimales")
        BigDecimal pesoBrutoKg,
        @Positive(message = "El número de bultos debe ser mayor a cero")
        Integer numeroBultos,
        @NotNull(message = "La modalidad de transporte es obligatoria")
        ModalidadTransporte modalidad,
        @Valid Transportista transportista,
        Boolean vehiculoM1l,
        @Valid Conductor conductor,
        @Pattern(regexp = "[A-Z0-9]{5,8}", message = "La placa va sin guion, ej. ABC123")
        String placa,
        @Size(max = 250, message = "La observación no puede superar 250 caracteres")
        String observacion,
        @Size(max = 500, message = "Máximo 500 prendas por guía")
        List<@Valid ItemCantidadRequest> items
) {
    public record Direccion(
            @NotBlank(message = "El ubigeo de llegada es obligatorio")
            @Pattern(regexp = "\\d{6}", message = "El ubigeo tiene 6 dígitos")
            String ubigeo,
            @NotBlank(message = "La dirección de llegada es obligatoria")
            @Size(max = 250, message = "La dirección no puede superar 250 caracteres")
            String direccion
    ) {
    }

    /** vehiculoM1l es opcional: si no se envía, el vehículo no es M1 ni L. */
    public boolean enVehiculoM1l() {
        return Boolean.TRUE.equals(vehiculoM1l);
    }

    public record Transportista(
            @NotBlank(message = "El RUC del transportista es obligatorio")
            String ruc,
            @NotBlank(message = "La razón social del transportista es obligatoria")
            @Size(max = 200, message = "La razón social no puede superar 200 caracteres")
            String razonSocial,
            @Size(max = 20, message = "El registro MTC no puede superar 20 caracteres")
            String registroMtc
    ) {
    }

    public record Conductor(
            @NotNull(message = "El tipo de documento del conductor es obligatorio")
            TipoDocumentoIdentidad tipoDocumento,
            @NotBlank(message = "El documento del conductor es obligatorio")
            String numeroDocumento,
            @NotBlank(message = "Los nombres del conductor son obligatorios")
            @Size(max = 100, message = "Los nombres no pueden superar 100 caracteres")
            String nombres,
            @NotBlank(message = "Los apellidos del conductor son obligatorios")
            @Size(max = 100, message = "Los apellidos no pueden superar 100 caracteres")
            String apellidos,
            @NotBlank(message = "La licencia de conducir es obligatoria")
            @Pattern(regexp = "[A-Z0-9]{9,10}", message = "La licencia tiene 9 o 10 caracteres, ej. Q45678912")
            String licencia
    ) {
    }
}
