package com.allstore.api.facturacion.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;

import java.time.OffsetDateTime;

/**
 * Detalle del envío de un documento a SUNAT (para el administrador). El documento es un
 * comprobante (comprobanteId) o un resumen de bajas (resumenId).
 *
 * @param documento descripción legible, ej. "Boleta de venta B001-00000015" o "Comunicación de baja RA-20261022-1"
 * @param ticket    solo resúmenes: número con el que se consulta el resultado en SUNAT
 */
public record EnvioSunatResponse(
        Long id,
        String documento,
        Long comprobanteId,
        TipoComprobante tipoComprobante,
        Long resumenId,
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
        boolean comprobante = e.getComprobante() != null;
        return new EnvioSunatResponse(
                e.getId(),
                e.getDocumento(),
                comprobante ? e.getComprobante().getId() : null,
                comprobante ? e.getComprobante().getTipoComprobante() : null,
                comprobante ? null : e.getResumen().getId(),
                comprobante ? e.getComprobante().getNumeroImpreso() : e.getResumen().getNumero(),
                e.getEstado(), e.getEntorno(), e.getNombreArchivo(), e.getHash(), e.getTicket(),
                e.getCodigoRespuesta(), e.getDescripcionRespuesta(), e.getObservaciones(), e.getIntentos(),
                e.getProximoIntento(), e.getFechaEnvio(), e.getFechaRespuesta(), e.tieneXml(), e.getCdr() != null);
    }
}
