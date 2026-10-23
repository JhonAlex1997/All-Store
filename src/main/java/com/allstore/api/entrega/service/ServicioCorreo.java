package com.allstore.api.entrega.service;

import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.entrega.config.EntregaProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Transporte de los correos según app.entrega.correo.modo: por el servidor SMTP (spring.mail.*) o,
 * en desarrollo, como archivos .eml en una carpeta (se abren con Outlook o Thunderbird).
 */
@Slf4j
@Component
public class ServicioCorreo {

    private final EntregaProperties properties;
    private final ObjectProvider<JavaMailSender> smtp;

    public ServicioCorreo(EntregaProperties properties, ObjectProvider<JavaMailSender> smtp) {
        this.properties = properties;
        this.smtp = smtp;
    }

    /** "De:" configurado; si no, el correo de la empresa. */
    public String remitente(Empresa empresa) {
        if (StringUtils.hasText(properties.correo().remitente())) {
            return properties.correo().remitente();
        }
        if (empresa != null && empresa.getEmail() != null) {
            return empresa.getEmail();
        }
        throw new IllegalStateException("Falta el remitente de los correos (CORREO_REMITENTE o el correo de la empresa)");
    }

    public MimeMessage nuevoMensaje() {
        JavaMailSender sender = smtp.getIfAvailable();
        return sender != null ? sender.createMimeMessage() : new MimeMessage(Session.getInstance(new Properties()));
    }

    /**
     * @param nombre en modo ARCHIVO, nombre del .eml (sin extensión)
     * @throws IllegalStateException si el correo está desactivado o falta el servidor SMTP
     */
    public void enviar(MimeMessage mensaje, String nombre) throws MessagingException, IOException {
        switch (properties.correo().modo()) {
            case DESACTIVADO -> throw new IllegalStateException("El envío por correo está desactivado");
            case SMTP -> {
                JavaMailSender sender = smtp.getIfAvailable();
                if (sender == null) {
                    throw new IllegalStateException("Falta configurar el servidor de correo (SPRING_MAIL_HOST)");
                }
                sender.send(mensaje);
            }
            case ARCHIVO -> {
                Path carpeta = Path.of(properties.correo().carpeta());
                Files.createDirectories(carpeta);
                mensaje.saveChanges();
                Path archivo = carpeta.resolve(nombre.replaceAll("[^A-Za-z0-9._-]", "_") + ".eml");
                try (OutputStream salida = Files.newOutputStream(archivo)) {
                    mensaje.writeTo(salida);
                }
                log.info("Correo guardado (modo ARCHIVO) en {}", archivo.toAbsolutePath());
            }
        }
    }
}
