package com.allstore.api.facturacion.service;

import com.allstore.api.facturacion.config.SunatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Envío directo a SUNAT por su servicio SOAP "billService" (operación sendBill): el XML va
 * comprimido en zip y en base64, con el usuario SOL en la cabecera WS-Security. La respuesta es
 * otro zip con la constancia de recepción (CDR), o una excepción SOAP si el envío no se procesó.
 */
@Component
@RequiredArgsConstructor
public class ClienteSoapSunat implements ServicioEnvioSunat {

    private static final Pattern CODIGO = Pattern.compile("(\\d{4})");

    private final SunatProperties properties;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    @Override
    public Respuesta enviar(String nombreArchivo, byte[] xmlFirmado) throws IOException {
        String zip = Base64.getEncoder().encodeToString(comprimir(nombreArchivo + ".xml", xmlFirmado));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(properties.urlEfectiva()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "urn:sendBill")
                .POST(HttpRequest.BodyPublishers.ofString(sobre(nombreArchivo.substring(0, 11), nombreArchivo + ".zip", zip),
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
        return interpretar(respuesta.body());
    }

    /** El usuario de WS-Security es el RUC seguido del usuario SOL (ej. 20000000001MODDATOS). */
    private String sobre(String ruc, String nombreZip, String contenidoBase64) {
        String usuario = escapar(ruc + (properties.usuarioSol() == null ? "" : properties.usuarioSol()));
        String clave = escapar(properties.claveSol() == null ? "" : properties.claveSol());
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" \
                xmlns:ser="http://service.sunat.gob.pe" \
                xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">\
                <soapenv:Header><wsse:Security><wsse:UsernameToken>\
                <wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password>\
                </wsse:UsernameToken></wsse:Security></soapenv:Header>\
                <soapenv:Body><ser:sendBill><fileName>%s</fileName><contentFile>%s</contentFile></ser:sendBill></soapenv:Body>\
                </soapenv:Envelope>""".formatted(usuario, clave, nombreZip, contenidoBase64);
    }

    /** Respuesta de SUNAT: CDR (procesado) o excepción SOAP (no procesado). */
    Respuesta interpretar(String cuerpo) throws IOException {
        Document soap = parsear(cuerpo);
        String applicationResponse = texto(soap, "applicationResponse");
        if (applicationResponse != null) {
            byte[] cdrZip = Base64.getDecoder().decode(applicationResponse.trim());
            Document cdr = parsear(new String(descomprimir(cdrZip), StandardCharsets.UTF_8));
            String codigo = textoNs(cdr, "ResponseCode");
            String descripcion = textoNs(cdr, "Description");
            List<String> notas = new ArrayList<>();
            NodeList elementos = cdr.getElementsByTagNameNS(GeneradorXmlUbl.NS_CBC, "Note");
            for (int i = 0; i < elementos.getLength(); i++) {
                notas.add(elementos.item(i).getTextContent().trim());
            }
            return new Respuesta(true, codigo, descripcion, notas, cdrZip);
        }
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

    static byte[] comprimir(String nombre, byte[] contenido) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry(nombre));
            zip.write(contenido);
            zip.closeEntry();
        }
        return salida.toByteArray();
    }

    /** Primer archivo .xml dentro del zip (la constancia se llama R-<nombre>.xml). */
    static byte[] descomprimir(byte[] zip) throws IOException {
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = entrada.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().endsWith(".xml")) {
                    return entrada.readAllBytes();
                }
            }
        }
        throw new IOException("El CDR de SUNAT no contiene un XML");
    }

    private static Document parsear(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Sin entidades externas: un XML de respuesta nunca debe poder leer archivos del servidor (XXE).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IOException("No se pudo leer la respuesta de SUNAT: " + e.getMessage(), e);
        }
    }

    /** Texto del primer elemento con ese nombre local, sin importar el espacio de nombres. */
    private static String texto(Document doc, String nombreLocal) {
        NodeList nodos = doc.getElementsByTagNameNS("*", nombreLocal);
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent();
    }

    private static String textoNs(Document doc, String nombreLocal) {
        NodeList nodos = doc.getElementsByTagNameNS(GeneradorXmlUbl.NS_CBC, nombreLocal);
        for (int i = 0; i < nodos.getLength(); i++) {
            Element e = (Element) nodos.item(i);
            if ("Response".equals(e.getParentNode().getLocalName())) {
                return e.getTextContent().trim();
            }
        }
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent().trim();
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
