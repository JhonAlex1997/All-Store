package com.allstore.api.entrega.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Entrega del comprobante al cliente ({@code app.entrega.*}). El servidor de correo (SMTP) se
 * configura con las propiedades estándar spring.mail.* (variables SPRING_MAIL_HOST,
 * SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD).
 *
 * @param urlPublica dirección con la que el cliente llega al sistema desde internet; con ella se
 *                   arman los enlaces de descarga (ej. https://comprobantes.mitienda.pe)
 */
@ConfigurationProperties(prefix = "app.entrega")
public record EntregaProperties(
        @DefaultValue("http://localhost:8080") String urlPublica,
        @DefaultValue Correo correo
) {
    /**
     * @param modo            DESACTIVADO, SMTP o ARCHIVO (desarrollo: guarda cada correo como .eml)
     * @param remitente       dirección "De:"; con SMTP suele tener que ser la misma cuenta que envía
     * @param carpeta         modo ARCHIVO: dónde se guardan los .eml
     * @param envioAutomatico enviar el comprobante al correo del cliente apenas SUNAT lo acepta
     * @param maxIntentos     intentos antes de marcar el correo como ERROR
     */
    public record Correo(
            @DefaultValue("DESACTIVADO") ModoCorreo modo,
            String remitente,
            @DefaultValue("correos") String carpeta,
            @DefaultValue("true") boolean envioAutomatico,
            @DefaultValue("5") int maxIntentos
    ) {
        public boolean habilitado() {
            return modo != ModoCorreo.DESACTIVADO;
        }
    }

    /** urlPublica sin la barra final, para armar enlaces. */
    public String base() {
        return urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }
}
