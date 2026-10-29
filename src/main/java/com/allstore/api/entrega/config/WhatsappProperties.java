package com.allstore.api.entrega.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Envío por WhatsApp con la API oficial de Meta, WhatsApp Cloud API ({@code app.whatsapp.*}).
 * Se necesita una cuenta de WhatsApp Business verificada, un número registrado y una plantilla
 * aprobada por Meta (los mensajes iniciados por la empresa solo pueden ser plantillas; Meta cobra
 * por conversación). La plantilla debe tener:
 * <ul>
 *   <li>encabezado de tipo DOCUMENTO (el PDF del comprobante);</li>
 *   <li>cuerpo con 4 variables, ej.: "Hola {{1}}, te enviamos tu {{2}} por {{3}}. Gracias por tu
 *       compra en {{4}}." → cliente, documento y número, total, nombre de la tienda.</li>
 * </ul>
 * Sin configurar, la caja comparte el enlace wa.me y el cajero envía el mensaje a mano.
 *
 * @param token         token de acceso de la app de Meta (secreto)
 * @param numeroId      "Phone number ID" del número de WhatsApp Business
 * @param plantilla     nombre de la plantilla aprobada
 * @param idioma        idioma de la plantilla (ej. es, es_PE)
 * @param urlApi        API Graph de Meta con su versión
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsappProperties(
        String token,
        String numeroId,
        @DefaultValue("comprobante_electronico") String plantilla,
        @DefaultValue("es") String idioma,
        @DefaultValue("https://graph.facebook.com/v26.0") String urlApi
) {
    public boolean configurado() {
        return StringUtils.hasText(token) && StringUtils.hasText(numeroId);
    }
}
