package com.allstore.api.alerta.dto;

import java.time.OffsetDateTime;

/**
 * Algo que el administrador debe atender.
 *
 * @param documento     el comprobante, la baja o la guía afectada; null en las alertas generales
 * @param desde         desde cuándo (emisión del documento); null si no aplica
 * @param comprobanteId para ir al comprobante (reemitir, anular o reenviar); null si no es uno
 * @param envioId       para ir al envío en el panel de SUNAT; null si no es uno
 */
public record Alerta(
        Nivel nivel,
        Tipo tipo,
        String documento,
        String mensaje,
        OffsetDateTime desde,
        Long comprobanteId,
        Long envioId
) {
    public enum Nivel {
        /** Hay algo sin validez ante SUNAT, o va a dejar de tenerla, o nada se está enviando. */
        CRITICA,
        /** Hay que atenderlo pronto para que no pase a crítico. */
        ADVERTENCIA
    }

    public enum Tipo {
        SUNAT_DESACTIVADO,
        CERTIFICADO,
        COMPROBANTE_RECHAZADO,
        ENVIO_EN_ERROR,
        PLAZO_DE_ENVIO,
        BAJA_RECHAZADA,
        GUIA_SIN_ACEPTAR
    }

    public static Alerta general(Nivel nivel, Tipo tipo, String mensaje) {
        return new Alerta(nivel, tipo, null, mensaje, null, null, null);
    }
}
