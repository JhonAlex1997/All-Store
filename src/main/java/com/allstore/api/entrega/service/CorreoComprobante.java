package com.allstore.api.entrega.service;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.venta.dto.VentaResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Contenido del correo con que se entrega un comprobante: asunto, texto (simple y HTML) y adjuntos
 * (PDF, XML firmado y constancia de SUNAT). Solo arma el mensaje; enviarlo es tarea de
 * {@link ServicioCorreo}.
 */
@Component
public class CorreoComprobante {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Adjunto(String nombre, byte[] contenido, String tipo) {
    }

    /**
     * @param enlace enlace público para descargar el PDF sin iniciar sesión
     */
    public void armar(MimeMessage mensaje, VentaResponse v, Empresa empresa, String destinatario, String remitente,
                      String enlace, List<Adjunto> adjuntos) throws MessagingException {
        String negocio = empresa.getNombreComercial() != null ? empresa.getNombreComercial() : empresa.getRazonSocial();
        String documento = v.tipoComprobante().getDescripcion();
        MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, true, "UTF-8");
        ayuda.setFrom(remitente);
        if (empresa.getEmail() != null) {
            ayuda.setReplyTo(empresa.getEmail());
        }
        ayuda.setTo(destinatario);
        ayuda.setSubject(documento + " " + v.numero() + " - " + negocio);

        List<String> parrafos = parrafos(v, documento, adjuntos);
        String firma = negocio + "\nRUC " + empresa.getRuc() + " · " + empresa.getDireccionFiscal()
                + (empresa.getTelefono() != null ? " · Tel. " + empresa.getTelefono() : "");
        String texto = saludo(v) + "\n\n" + String.join("\n", parrafos) + "\n\nDescárgalo aquí: " + enlace + "\n\n" + firma;
        String html = "<p>" + esc(saludo(v)) + "</p>"
                + parrafos.stream().map(p -> "<p>" + esc(p) + "</p>").collect(Collectors.joining())
                + "<p><a href=\"" + esc(enlace) + "\">Descargar " + esc(documento.toLowerCase()) + " " + esc(v.numero()) + "</a></p>"
                + "<p style=\"color:#666;font-size:12px\">" + esc(firma).replace("\n", "<br>") + "</p>";
        ayuda.setText(texto, html);
        for (Adjunto a : adjuntos) {
            ayuda.addAttachment(a.nombre(), new ByteArrayResource(a.contenido()), a.tipo());
        }
    }

    private static String saludo(VentaResponse v) {
        boolean generico = TipoDocumentoIdentidad.SIN_DOCUMENTO.getCodigoSunat().equals(v.cliente().tipoDocumento());
        return generico ? "Hola:" : "Hola, " + v.cliente().nombre() + ":";
    }

    private static List<String> parrafos(VentaResponse v, String documento, List<Adjunto> adjuntos) {
        String fecha = FECHA.format(v.fechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA));
        String principal = "Te enviamos tu " + documento.toLowerCase() + " " + v.numero() + " del " + fecha
                + " por S/ " + dinero(v.total()) + (v.comprobanteOrigen() != null && v.tipoComprobante().isElectronico()
                ? ", que modifica a " + v.comprobanteOrigen() : "") + ".";
        String archivos = "Adjuntamos " + adjuntos.stream().map(CorreoComprobante::descripcion)
                .collect(Collectors.collectingAndThen(Collectors.toList(), CorreoComprobante::enumerar)) + ".";
        String validez = v.tipoComprobante().isElectronico()
                ? "Puedes verificar su validez en www.sunat.gob.pe."
                : "Es un documento interno: no es un comprobante de pago.";
        return List.of(principal, archivos, validez);
    }

    private static String descripcion(Adjunto a) {
        if (a.nombre().endsWith(".pdf")) {
            return "la representación impresa (PDF)";
        }
        return a.nombre().startsWith("R-") ? "la constancia de recepción de SUNAT (CDR)" : "el archivo electrónico (XML)";
    }

    /** "a", "a y b", "a, b y c". */
    private static String enumerar(List<String> partes) {
        if (partes.size() <= 1) {
            return String.join("", partes);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.getLast();
    }

    private static String dinero(BigDecimal monto) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(monto);
    }

    private static String esc(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }
}
