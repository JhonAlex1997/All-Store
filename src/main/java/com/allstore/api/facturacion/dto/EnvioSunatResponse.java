package com.allstore.api.facturacion.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;

import java.time.OffsetDateTime;

/**
 * Detalle del envío de un documento a SUNAT (para el administrador). El documento es un
 * comprobante (comprobanteId), un resumen de bajas (resumenId) o una guía de remisión (guiaId).
 *
 * @param documento descripción legible, ej. "Boleta de venta B001-00000015" o "Comunicación de baja RA-20261022-1"
 * @param ticket    resúmenes y guías: número con el que se consulta el resultado en SUNAT
 */
public record EnvioSunatResponse(
        Long id,
        String documento,
        Long comprobanteId,
        TipoComprobante tipoComprobante,
        Long resumenId,
        Long guiaId,
        String numero,
        EstadoSunat estado,
        EntornoSunat entorno,
        String nombreArchivo,
        String hash,
        String ticket,
        String codigoRespuesta,
        String descripcionRespuesta,
        String observaciones,
        int intentos,
        OffsetDateTime proximoIntento,
        OffsetDateTime fechaEnvio,
        OffsetDateTime fechaRespuesta,
        boolean tieneXml,
        boolean tieneCdr
) {
    public static EnvioSunatResponse from(EnvioSunat e) {
        return new EnvioSunatResponse(
                e.getId(),
                e.getDocumento(),
                e.getComprobante() != null ? e.getComprobante().getId() : null,
                e.getComprobante() != null ? e.getComprobante().getTipoComprobante() : null,
                e.getResumen() != null ? e.getResumen().getId() : null,
                e.getGuia() != null ? e.getGuia().getId() : null,
                e.getNumero(),
                e.getEstado(), e.getEntorno(), e.getNombreArchivo(), e.getHash(), e.getTicket(),
                e.getCodigoRespuesta(), e.getDescripcionRespuesta(), e.getObservaciones(), e.getIntentos(),
                e.getProximoIntento(), e.getFechaEnvio(), e.getFechaRespuesta(), e.tieneXml(), e.getCdr() != null);
    }
}
