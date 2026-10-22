package com.allstore.api.facturacion.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Archivos que se intercambian con SUNAT: el zip con el XML que se envía y la constancia de
 * recepción (CDR) que devuelve, un zip con el XML ApplicationResponse. Lo usan el servicio SOAP
 * (comprobantes y resúmenes) y la API REST (guías).
 */
final class CdrSunat {

    private CdrSunat() {
    }

    /** Zip con un solo archivo, como lo pide SUNAT. */
    static byte[] comprimir(String nombre, byte[] contenido) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry(nombre));
            zip.write(contenido);
            zip.closeEntry();
        }
        return salida.toByteArray();
    }

    /** Código, descripción y observaciones de la constancia. */
    static ServicioEnvioSunat.Respuesta leer(byte[] cdrZip) throws IOException {
        Document cdr = parsear(new String(descomprimir(cdrZip), StandardCharsets.UTF_8));
        String codigo = textoDeRespuesta(cdr, "ResponseCode");
        String descripcion = textoDeRespuesta(cdr, "Description");
        List<String> notas = new ArrayList<>();
        NodeList elementos = cdr.getElementsByTagNameNS(XmlUbl.NS_CBC, "Note");
        for (int i = 0; i < elementos.getLength(); i++) {
            notas.add(elementos.item(i).getTextContent().trim());
        }
        return new ServicioEnvioSunat.Respuesta(true, codigo, descripcion, notas, cdrZip);
    }

    /**
     * Enlace de consulta que SUNAT incluye en la constancia de la guía de remisión: es el
     * contenido del código QR de su representación impresa.
     */
    static Optional<String> enlaceConsulta(byte[] cdrZip) {
        try {
            Document cdr = parsear(new String(descomprimir(cdrZip), StandardCharsets.UTF_8));
            NodeList descripciones = cdr.getElementsByTagNameNS(XmlUbl.NS_CBC, "DocumentDescription");
            for (int i = 0; i < descripciones.getLength(); i++) {
                String texto = descripciones.item(i).getTextContent().trim();
                if (texto.startsWith("http")) {
                    return Optional.of(texto);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
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

    static Document parsear(String xml) throws IOException {
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

    /** Texto del elemento dentro de cac:Response (si no hay, el primero que aparezca). */
    private static String textoDeRespuesta(Document doc, String nombreLocal) {
        NodeList nodos = doc.getElementsByTagNameNS(XmlUbl.NS_CBC, nombreLocal);
        for (int i = 0; i < nodos.getLength(); i++) {
            Element e = (Element) nodos.item(i);
            Node padre = e.getParentNode();
            if ("Response".equals(padre.getLocalName())) {
                return e.getTextContent().trim();
            }
        }
        return nodos.getLength() == 0 ? null : nodos.item(0).getTextContent().trim();
    }
}
