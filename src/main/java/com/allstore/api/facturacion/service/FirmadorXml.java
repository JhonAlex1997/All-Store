package com.allstore.api.facturacion.service;

import com.allstore.api.facturacion.config.SunatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Firma el XML con el certificado digital de la empresa (firma XMLDSig "enveloped" dentro de
 * ext:ExtensionContent, como pide SUNAT). El resumen (DigestValue) de la firma es el "hash"
 * que se imprime en el comprobante y va en su código QR.
 *
 * <p>Sin certificado configurado no firma (los comprobantes quedan pendientes hasta que se
 * configure). Con un certificado mal configurado, el sistema no arranca.
 */
@Slf4j
@Component
public class FirmadorXml {

    private final PrivateKey clavePrivada;
    private final X509Certificate certificado;

    public record XmlFirmado(String xml, String hash) {
    }

    public FirmadorXml(SunatProperties properties) {
        if (!properties.tieneCertificado()) {
            this.clavePrivada = null;
            this.certificado = null;
            log.info("SUNAT: sin certificado digital configurado; los comprobantes no se firmarán");
            return;
        }
        try (InputStream entrada = Files.newInputStream(Path.of(properties.certificadoRuta()))) {
            char[] clave = properties.certificadoClave() == null ? new char[0] : properties.certificadoClave().toCharArray();
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(entrada, clave);
            String alias = Collections.list(almacen.aliases()).stream()
                    .filter(a -> isKeyEntry(almacen, a)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("El certificado no contiene una clave privada"));
            this.clavePrivada = (PrivateKey) almacen.getKey(alias, clave);
            this.certificado = (X509Certificate) almacen.getCertificate(alias);
            log.info("SUNAT: certificado cargado ({}), vence el {}", certificado.getSubjectX500Principal().getName(),
                    certificado.getNotAfter());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el certificado digital de SUNAT ("
                    + properties.certificadoRuta() + "): revisa la ruta y la clave", e);
        }
    }

    public boolean disponible() {
        return clavePrivada != null;
    }

    public XmlFirmado firmar(Document doc) {
        if (!disponible()) {
            throw new IllegalStateException("No hay certificado digital configurado para firmar");
        }
        try {
            XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");
            Reference referencia = fabrica.newReference("",
                    fabrica.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo info = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                    List.of(referencia));
            KeyInfoFactory claves = fabrica.getKeyInfoFactory();
            KeyInfo keyInfo = claves.newKeyInfo(List.of(claves.newX509Data(List.of(certificado))));

            DOMSignContext contexto = new DOMSignContext(clavePrivada, contenidoDeExtension(doc));
            contexto.setDefaultNamespacePrefix("ds");
            XMLSignature firma = fabrica.newXMLSignature(info, keyInfo, null, GeneradorXmlUbl.ID_FIRMA, null);
            firma.sign(contexto);

            String hash = Base64.getEncoder().encodeToString(referencia.getDigestValue());
            return new XmlFirmado(serializar(doc), hash);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el XML: " + e.getMessage(), e);
        }
    }

    private static Node contenidoDeExtension(Document doc) {
        NodeList nodos = doc.getElementsByTagNameNS(GeneradorXmlUbl.NS_EXT, "ExtensionContent");
        if (nodos.getLength() == 0) {
            throw new IllegalStateException("El XML no tiene ext:ExtensionContent para la firma");
        }
        return nodos.item(0);
    }

    /** Sin sangrías ni espacios agregados: cualquier cambio después de firmar invalida la firma. */
    static String serializar(Document doc) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter salida = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(salida));
        return salida.toString();
    }

    private static boolean isKeyEntry(KeyStore almacen, String alias) {
        try {
            return almacen.isKeyEntry(alias);
        } catch (Exception e) {
            return false;
        }
    }
}
