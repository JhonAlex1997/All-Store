package com.allstore.api.entrega.dto;

import com.allstore.api.entrega.entity.EnvioCorreo;
import com.allstore.api.entrega.entity.EstadoCorreo;

import java.time.OffsetDateTime;

/**
 * @param automatico verdadero si lo envió el sistema al aceptar SUNAT el comprobante
 * @param pedidoPor  usuario que lo originó: quien lo pidió o, si fue automático, quien envió el
 *                   comprobante a SUNAT ("sistema" si fue el proceso programado)
 * @param error      último error del servidor de correo (se reintentará si sigue PENDIENTE)
 */
public record EnvioCorreoResponse(
        Long id,
        String destinatario,
        EstadoCorreo estado,
        boolean automatico,
        int intentos,
        OffsetDateTime fechaSolicitud,
        String pedidoPor,
        OffsetDateTime fechaEnvio,
        String error
) {
    public static EnvioCorreoResponse from(EnvioCorreo e) {
        return new EnvioCorreoResponse(e.getId(), e.getDestinatario(), e.getEstado(), e.isAutomatico(), e.getIntentos(),
                e.getCreatedAt(), e.getCreatedBy(), e.getFechaEnvio(), e.getError());
    }
}
