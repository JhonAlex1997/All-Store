package com.allstore.api.facturacion.service;

import com.allstore.api.empresa.entity.Empresa;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Piezas comunes de todos los XML que se envían a SUNAT (comprobantes, resúmenes y guías):
 * espacios de nombres, creación de elementos y el bloque que referencia la firma digital.
 */
final class XmlUbl {

    static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String NS_SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";

    static final String MONEDA = "PEN";

    /** Id de la firma: el bloque cac:Signature la referencia y el firmador la crea con este Id. */
    static final String ID_FIRMA = "SignatureSP";

    private XmlUbl() {
    }

    /**
     * Documento nuevo con su raíz y el lugar donde irá la firma (ext:UBLExtensions).
     *
     * <p>Todas las declaraciones de espacio de nombres van como atributos explícitos, incluida la
     * principal: la firma se calcula sobre el DOM en memoria, y si el serializador agregara una
     * declaración que no estaba, el XML enviado ya no coincidiría con el firmado (SUNAT lo
     * rechaza con 2335 "Incorrect reference digest value").
     */
    static Element raiz(String espacioNombres, String nombre, boolean conSac) {
        Document doc = nuevoDocumento();
        Element raiz = doc.createElementNS(espacioNombres, nombre);
        doc.appendChild(raiz);
        raiz.setAttributeNS(NS_XMLNS, "xmlns", espacioNombres);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:cac", NS_CAC);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:cbc", NS_CBC);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:ds", NS_DS);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:ext", NS_EXT);
        if (conSac) {
            raiz.setAttributeNS(NS_XMLNS, "xmlns:sac", NS_SAC);
        }
        Element extensiones = hijo(raiz, NS_EXT, "ext:UBLExtensions");
        hijo(hijo(extensiones, NS_EXT, "ext:UBLExtension"), NS_EXT, "ext:ExtensionContent");
        return raiz;
    }

    /** Bloque cac:Signature: quién firma y dónde está la firma (la agrega FirmadorXml). */
    static void firma(Element raiz, Empresa empresa) {
        Element firma = cac(raiz, "Signature");
        cbc(firma, "ID", ID_FIRMA);
        Element firmante = cac(firma, "SignatoryParty");
        cbc(cac(firmante, "PartyIdentification"), "ID", empresa.getRuc());
        cbc(cac(firmante, "PartyName"), "Name", empresa.getRazonSocial());
        cbc(cac(cac(firma, "DigitalSignatureAttachment"), "ExternalReference"), "URI", "#" + ID_FIRMA);
    }

    static Element hijo(Element padre, String ns, String nombre) {
        Element e = padre.getOwnerDocument().createElementNS(ns, nombre);
        padre.appendChild(e);
        return e;
    }

    static Element cac(Element padre, String nombre) {
        return hijo(padre, NS_CAC, "cac:" + nombre);
    }

    static Element cbc(Element padre, String nombre, String valor) {
        Element e = hijo(padre, NS_CBC, "cbc:" + nombre);
        e.setTextContent(valor);
        return e;
    }

    static Element sac(Element padre, String nombre) {
        return hijo(padre, NS_SAC, "sac:" + nombre);
    }

    static Element sac(Element padre, String nombre, String valor) {
        Element e = sac(padre, nombre);
        e.setTextContent(valor);
        return e;
    }

    /** Importe con 2 decimales y moneda. */
    static Element monto(Element padre, String nombre, BigDecimal valor) {
        Element e = cbc(padre, nombre, dosDecimales(valor));
        e.setAttribute("currencyID", MONEDA);
        return e;
    }

    static String dosDecimales(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Document nuevoDocumento() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo crear el documento XML", e);
        }
    }
}
