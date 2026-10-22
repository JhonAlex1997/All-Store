package com.allstore.api.facturacion.service;

import com.allstore.api.facturacion.config.SunatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envío directo a SUNAT por su servicio SOAP "billService": el XML va comprimido en zip y en
 * base64, con el usuario SOL en la cabecera WS-Security.
 * <ul>
 *   <li>sendBill (comprobantes): responde en el acto con otro zip que trae la constancia de
 *       recepción (CDR), o con una excepción SOAP si el envío no se procesó.</li>
 *   <li>sendSummary (comunicación de baja, resumen diario): responde con un ticket.</li>
 *   <li>getStatus: estado del ticket; 98 = en proceso, 0 o 99 = terminado, con su CDR.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ClienteSoapSunat implements ServicioEnvioSunat {

    private static final Pattern CODIGO = Pattern.compile("(\\d{4})");

    private final SunatProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Override
    public Respuesta enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException {
        return interpretar(llamar("sendBill", nombreArchivo, operacionConArchivo("sendBill", nombreArchivo, xmlFirmado)));
    }

    @Override
    public Ticket enviarResumen(String nombreArchivo, byte[] xmlFirmado) throws IOException {
        String cuerpo = llamar("sendSummary", nombreArchivo, operacionConArchivo("sendSummary", nombreArchivo, xmlFirmado));
        Document soap = CdrSunat.parsear(cuerpo);
        String ticket = texto(soap, "ticket");
        if (ticket != null && !ticket.isBlank()) {
            return new Ticket(ticket.trim(), null);
        }
        return new Ticket(null, excepcion(soap, cuerpo));
    }

    @Override
    public EstadoTicket consultarTicket(String nombreArchivo, String ticket) throws IOException {
        String operacion = "<ser:getStatus><ticket>" + escapar(ticket) + "</ticket></ser:getStatus>";
        return interpretarEstado(llamar("getStatus", nombreArchivo, operacion));
    }

    private static String operacionConArchivo(String operacion, String nombreArchivo, byte[] xmlFirmado) throws IOException {
        String zip = Base64.getEncoder().encodeToString(CdrSunat.comprimir(nombreArchivo + ".xml", xmlFirmado));
        return "<ser:%s><fileName>%s.zip</fileName><contentFile>%s</contentFile></ser:%s>"
                .formatted(operacion, nombreArchivo, zip, operacion);
    }

    /** Llama a una operación del servicio y devuelve el cuerpo de la respuesta (con o sin excepción SOAP). */
    private String llamar(String operacion, String nombreArchivo, String cuerpoOperacion) throws IOException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(properties.urlEfectiva()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:" + operacion)
                .POST(HttpRequest.BodyPublishers.ofString(sobre(nombreArchivo.substring(0, 11), cuerpoOperacion),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Envío interrumpido", e);
        }
        if (respuesta.statusCode() >= 500 && !respuesta.body().contains("Fault")) {
            throw new IOException("SUNAT respondió HTTP " + respuesta.statusCode());
        }
        return respuesta.body();
    }

    /** El usuario de WS-Security es el RUC seguido del usuario SOL (ej. 20000000001MODDATOS). */
    private String sobre(String ruc, String cuerpoOperacion) {
        String usuario = escapar(ruc + (properties.usuarioSol() == null ? "" : properties.usuarioSol()));
        String clave = escapar(properties.claveSol() == null ? "" : properties.claveSol());
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
                xmlns:ser="http://service.sunat.gob.pe" \
                xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">\
                <soapenv:Header><wsse:Security><wsse:UsernameToken>\
                <wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password>\
                </wsse:UsernameToken></wsse:Security></soapenv:Header>\
                <soapenv:Body>%s</soapenv:Body>\
                </soapenv:Envelope>""".formatted(usuario, clave, cuerpoOperacion);
    }

    /** Respuesta de sendBill: CDR (procesado) o excepción SOAP (no procesado). */
    Respuesta interpretar(String cuerpo) throws IOException {
        Document soap = CdrSunat.parsear(cuerpo);
        String applicationResponse = texto(soap, "applicationResponse");
        if (applicationResponse != null) {
            return leerCdr(applicationResponse);
        }
        return excepcion(soap, cuerpo);
    }

    /** Respuesta de getStatus: statusCode 98 = en proceso; 0 (aceptado) o 99 (con errores) traen el CDR. */
    EstadoTicket interpretarEstado(String cuerpo) throws IOException {
        Document soap = CdrSunat.parsear(cuerpo);
        String estado = texto(soap, "statusCode");
        if (estado == null) {
            return EstadoTicket.terminado(excepcion(soap, cuerpo));
        }
        if ("98".equals(estado.trim())) {
            return EstadoTicket.procesando();
        }
        String contenido = texto(soap, "content");
        if (contenido == null || contenido.isBlank()) {
            return EstadoTicket.terminado(new Respuesta(false, estado.trim(),
                    "SUNAT respondió el estado " + estado.trim() + " sin constancia", List.of(), null));
        }
        return EstadoTicket.terminado(leerCdr(contenido));
    }

    /** Constancia (CDR): zip en base64 con el XML ApplicationResponse. */
    private static Respuesta leerCdr(String base64) throws IOException {
        return CdrSunat.leer(Base64.getDecoder().decode(base64.trim()));
    }

    /** Excepción SOAP: SUNAT no registró el documento. El código va en faultcode o en el mensaje. */
    private static Respuesta excepcion(Document soap, String cuerpo) throws IOException {
        String faultcode = texto(soap, "faultcode");
        String faultstring = texto(soap, "faultstring");
        if (faultcode == null && faultstring == null) {
            throw new IOException("Respuesta de SUNAT no reconocida: " + cuerpo.substring(0, Math.min(300, cuerpo.length())));
        }
        String codigo = extraerCodigo(faultcode);
        if (codigo == null) {
            codigo = extraerCodigo(faultstring);
        }
        return new Respuesta(false, codigo == null ? "" : codigo, faultstring, List.of(), null);
    }

    private static String extraerCodigo(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = CODIGO.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    /** Texto del primer elemento con ese nombre local, sin importar el espacio de nombres. */
    private static String texto(Document doc, String nombreLocal) {
        NodeList nodos = doc.getElementsByTagNameNS("*", nombreLocal);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
