package com.allstore.api.alerta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Alertas para el administrador ({@code app.alertas.*}).
 *
 * @param correos              a quiénes llega el resumen de alertas; vacío = al correo de la empresa
 * @param horario              cuándo se envía (cron, hora de Lima); por defecto a las 8:00 y a las 18:00
 * @param diasPlazoEnvio       días calendario que da SUNAT para recibir un comprobante después de su
 *                             emisión. SUNAT indica 3 para facturas y notas; se usa el mismo para todos
 *                             (avisar antes de tiempo es lo prudente)
 * @param diasAvisoCertificado con cuánta anticipación avisar que vence el certificado digital
 */
@ConfigurationProperties(prefix = "app.alertas")
public record AlertaProperties(
        List<String> correos,
        @DefaultValue("0 0 8,18 * * *") String horario,
        @DefaultValue("3") int diasPlazoEnvio,
        @DefaultValue("30") int diasAvisoCertificado
) {
    public List<String> destinatarios() {
        return correos == null ? List.of() : correos.stream().map(String::trim).filter(c -> !c.isEmpty()).toList();
    }
}
