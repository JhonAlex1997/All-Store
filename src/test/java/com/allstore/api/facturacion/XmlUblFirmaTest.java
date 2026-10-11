package com.allstore.api.facturacion;

import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.service.FirmadorXml;
import com.allstore.api.facturacion.service.GeneradorXmlUbl;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.MotivoNotaCredito;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML UBL y firma, sin base de datos. La prueba clave vuelve a leer el XML desde el texto (como
 * lo recibe SUNAT) y valida la firma: si el XML enviado difiriera del firmado, fallaría aquí y no
 * en SUNAT con el error 2335.
 */
class XmlUblFirmaTest {

    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private final GeneradorXmlUbl generador = new GeneradorXmlUbl();
    private final FirmadorXml firmador = new FirmadorXml(new SunatProperties(false, EntornoSunat.BETA, null, null, null,
            "config/sunat/certificado-beta-prueba.p12", "prueba-beta", 30000, 8));

    @Test
    void laFirmaSigueValidaDespuesDeSerializarYVolverALeer() throws Exception {
        FirmadorXml.XmlFirmado firmado = firmador.firmar(generador.generar(boleta(), empresa()));

        Document leido = parsear(firmado.xml());
        NodeList firmas = leido.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        assertThat(firmas.getLength()).isEqualTo(1);
        DOMValidateContext contexto = new DOMValidateContext(new SelectorX509(), firmas.item(0));
        XMLSignature firma = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);
        assertThat(firma.validate(contexto)).isTrue();
        assertThat(firmado.hash()).isNotBlank();
    }

    @Test
    void boletaLlevaLosDatosYTotalesQuePideSunat() throws Exception {
        Document xml = parsear(firmador.firmar(generador.generar(boleta(), empresa())).xml());

        assertThat(xml.getDocumentElement().getLocalName()).isEqualTo("Invoice");
        assertThat(cbc(xml, "ID")).isEqualTo("B001-15");
        assertThat(cbc(xml, "InvoiceTypeCode")).isEqualTo("03");
        assertThat(cbc(xml, "Note")).isEqualTo("CIENTO SESENTA Y NUEVE CON 80/100 SOLES");
        assertThat(cbc(xml, "PayableAmount")).isEqualTo("169.80");
        // Descuento de la línea, sin IGV: 2 x 76.1864... = 152.37 - 143.90 = 8.47
        assertThat(cbc(xml, "Amount")).isEqualTo("8.47");
        assertThat(xml.getElementsByTagNameNS(NS_CBC, "PaymentMeansID").getLength())
                .as("la forma de pago solo va en facturas").isZero();
    }

    @Test
    void notaDeCreditoReferenciaAlComprobanteYSuMotivo() throws Exception {
        Comprobante boleta = boleta();
        Comprobante nota = boleta();
        nota.setTipoComprobante(TipoComprobante.NOTA_CREDITO);
        nota.setSerie("BC01");
        nota.setCorrelativo(3);
        nota.setComprobanteOrigen(boleta);
        nota.setMotivoNota(MotivoNotaCredito.DEVOLUCION_POR_ITEM);
        nota.setSustento("Cambio de talla");

        Document xml = parsear(firmador.firmar(generador.generar(nota, empresa())).xml());

        assertThat(xml.getDocumentElement().getLocalName()).isEqualTo("CreditNote");
        assertThat(cbc(xml, "ReferenceID")).isEqualTo("B001-15");
        assertThat(cbc(xml, "ResponseCode")).isEqualTo("07");
        assertThat(cbc(xml, "DocumentTypeCode")).isEqualTo("03");
        assertThat(xml.getElementsByTagNameNS(NS_CBC, "CreditedQuantity").getLength()).isEqualTo(1);
        // En notas de crédito el descuento va dentro del valor unitario (SUNAT no resta el
        // AllowanceCharge de la línea): 143.90 / 2 = 71.95.
        assertThat(ordenDeHijos(xml, "CreditNoteLine")).doesNotContain("AllowanceCharge");
        var precios = xml.getElementsByTagNameNS(NS_CBC, "PriceAmount");
        assertThat(precios.item(precios.getLength() - 1).getTextContent()).isEqualTo("71.95");
    }

    @Test
    void enLaLineaDeFacturaElDescuentoVaAntesQueLosImpuestos() throws Exception {
        Document xml = parsear(firmador.firmar(generador.generar(boleta(), empresa())).xml());
        assertThat(ordenDeHijos(xml, "InvoiceLine")).containsSubsequence("PricingReference", "AllowanceCharge", "TaxTotal", "Item");
    }

    private static java.util.List<String> ordenDeHijos(Document xml, String elemento) {
        var linea = xml.getElementsByTagNameNS("urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
                elemento).item(0);
        java.util.List<String> nombres = new java.util.ArrayList<>();
        for (var hijo = linea.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            nombres.add(hijo.getLocalName());
        }
        return nombres;
    }

    @Test
    void nombreDeArchivoSegunSunat() {
        assertThat(GeneradorXmlUbl.nombreArchivo(boleta(), empresa())).isEqualTo("20000000001-03-B001-15");
    }

    // ---------- Datos ----------

    private static Empresa empresa() {
        Empresa e = new Empresa();
        e.setRuc("20000000001");
        e.setRazonSocial("EMPRESA DEMO S.A.C.");
        e.setNombreComercial("AllStore & Co.");
        e.setDireccionFiscal("Jr. Gamarra 100, La Victoria");
        e.setUbigeo("150115");
        return e;
    }

    /** Boleta de 2 jeans a 89.90 con S/ 10 de descuento: total 169.80, base 143.90, IGV 25.90. */
    private static Comprobante boleta() {
        Comprobante c = new Comprobante();
        c.setTipoComprobante(TipoComprobante.BOLETA);
        c.setSerie("B001");
        c.setCorrelativo(15);
        c.setFechaEmision(OffsetDateTime.of(2026, 10, 10, 15, 30, 0, 0, ZoneOffset.ofHours(-5)));
        c.setClienteTipoDocumento("1");
        c.setClienteNumeroDocumento("45678912");
        c.setClienteNombre("Juan Pérez Rojas");
        ComprobanteDetalle d = new ComprobanteDetalle();
        d.setSku("MOM01-AZU-28");
        d.setDescripcion("Jean Mom Fit Clásico - Azul talla 28");
        d.setUnidadMedida("NIU");
        d.setTipoAfectacionIgv("10");
        d.setCantidad(2);
        d.setPrecioUnitario(new BigDecimal("89.90"));
        d.setDescuento(new BigDecimal("10.00"));
        d.setValorVenta(new BigDecimal("143.90"));
        d.setIgv(new BigDecimal("25.90"));
        d.setTotal(new BigDecimal("169.80"));
        c.agregarDetalle(d);
        c.recalcularTotales();
        return c;
    }

    private static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String cbc(Document xml, String nombre) {
        return xml.getElementsByTagNameNS(NS_CBC, nombre).item(0).getTextContent();
    }

    /** Toma la clave pública del certificado incluido en la firma. */
    private static class SelectorX509 extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) {
            for (Object contenido : keyInfo.getContent()) {
                if (contenido instanceof X509Data datos) {
                    for (Object o : datos.getContent()) {
                        if (o instanceof X509Certificate cert) {
                            return cert::getPublicKey;
                        }
                    }
                }
            }
            throw new IllegalStateException("La firma no incluye el certificado");
        }
    }
}
