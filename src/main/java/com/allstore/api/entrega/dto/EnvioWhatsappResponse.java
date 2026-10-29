package com.allstore.api.entrega.dto;

import com.allstore.api.entrega.entity.EnvioWhatsapp;

import java.time.OffsetDateTime;

/**
 * Resultado de enviar un comprobante por WhatsApp.
 *
 * @param modo     AUTOMATICO (lo envió el sistema por la API de Meta) o ENLACE (la API no está
 *                 configurada o falló: la caja abre el enlace y envía el mensaje a mano)
 * @param estado   ENVIADO o ERROR (solo en modo AUTOMATICO)
 * @param enlace   enlace wa.me con el mensaje listo (en modo ENLACE, o como respaldo si falló)
 */
public record EnvioWhatsappResponse(
        String modo,
        String telefono,
        String estado,
        String mensajeId,
        String error,
        String enlace,
        OffsetDateTime fecha,
        String pedidoPor
) {
    public static EnvioWhatsappResponse from(EnvioWhatsapp e, String enlace) {
        return new EnvioWhatsappResponse("AUTOMATICO", e.getTelefono(), e.getEstado().name(), e.getMensajeId(), e.getError(),
                e.getEstado() == EnvioWhatsapp.Estado.ERROR ? enlace : null, e.getCreatedAt(), e.getCreatedBy());
    }

    public static EnvioWhatsappResponse enlace(String telefono, String enlace) {
        return new EnvioWhatsappResponse("ENLACE", telefono, null, null, null, enlace, null, null);
    }
}
