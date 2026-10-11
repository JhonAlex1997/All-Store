package com.allstore.api.facturacion.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;

import java.time.OffsetDateTime;

/** Detalle del envío de un comprobante a SUNAT (para el administrador). */
public record EnvioSunatResponse(
        Long comprobanteId,
        TipoComprobante tipoComprobante,
        String numero,
        EstadoSunat estado,
        EntornoSunat entorno,
        String nombreArchivo,
        String hash,
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
        return new EnvioSunatResponse(e.getComprobante().getId(), e.getComprobante().getTipoComprobante(),
                e.getComprobante().getNumeroImpreso(), e.getEstado(), e.getEntorno(), e.getNombreArchivo(), e.getHash(),
                e.getCodigoRespuesta(), e.getDescripcionRespuesta(), e.getObservaciones(), e.getIntentos(),
                e.getProximoIntento(), e.getFechaEnvio(), e.getFechaRespuesta(), e.tieneXml(), e.getCdr() != null);
    }
}
