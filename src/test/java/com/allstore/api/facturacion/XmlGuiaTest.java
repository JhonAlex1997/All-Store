package com.allstore.api.facturacion;

import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.config.EntornoSunat;
import com.allstore.api.facturacion.config.SunatProperties;
import com.allstore.api.facturacion.service.FirmadorXml;
import com.allstore.api.facturacion.service.GeneradorGuiaXml;
import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.guia.entity.GuiaRemisionDetalle;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.guia.entity.MotivoTraslado;
import com.allstore.api.venta.entity.Comprobante;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML de la guía de remisión (formato GRE 2022), sin base de datos. SUNAT no tiene entorno de
 * pruebas para este formato, así que aquí se verifica con detalle la estructura que exige su guía
 * de elaboración: orden de elementos, catálogos y la firma.
 */
class XmlGuiaTest {

    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    private final GeneradorGuiaXml generador = new GeneradorGuiaXml();
    private final FirmadorXml firmador = new FirmadorXml(new SunatProperties(false, EntornoSunat.BETA, null, null, null, null,
            "config/sunat/certificado-beta-prueba.p12", "prueba-beta", 30000, 8, null));

    @Test
    void trasladoEntreLocalesEnAutoConCodigosDeEstablecimiento() throws Exception {
        String texto = firmador.firmar(generador.generar(traslado(), empresa())).xml();
        Document xml = parsear(texto);

        assertThat(firmaValida(xml)).isTrue();
        assertThat(xml.getDocumentElement().getLocalName()).isEqualTo("DespatchAdvice");
        assertThat(cbc(xml, "UBLVersionID")).isEqualTo("2.1");
        assertThat(cbc(xml, "CustomizationID")).isEqualTo("2.0");
        assertThat(cbc(xml, "ID")).isEqualTo("T001-7");
        assertThat(cbc(xml, "DespatchAdviceTypeCode")).isEqualTo("09");
        assertThat(cbc(xml, "HandlingCode")).isEqualTo("04");
        assertThat(cbc(xml, "GrossWeightMeasure")).isEqualTo("12.500");
        assertThat(cbc(xml, "SpecialInstructions")).isEqualTo("SUNAT_Envio_IndicadorTrasladoVehiculoM1L");
        assertThat(cbc(xml, "TransportModeCode")).isEqualTo("02");
        // Código de anexo de llegada (tienda 0000) y de partida (almacén 0001), con el RUC del emisor.
        var anexos = xml.getElementsByTagNameNS(NS_CBC, "AddressTypeCode");
        assertThat(anexos.getLength()).isEqualTo(2);
        assertThat(anexos.item(0).getTextContent()).isEqualTo("0000");
        assertThat(anexos.item(1).getTextContent()).isEqualTo("0001");
        assertThat(((Element) anexos.item(1)).getAttribute("listID")).isEqualTo("20000000001");
        assertThat(((Element) xml.getElementsByTagNameNS(NS_CBC, "HandlingCode").item(0)).getAttribute("listURI"))
                .isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo20");
        assertThat(texto).doesNotContain("<cac:DriverPerson", "<cac:CarrierParty", "<cac:TransportHandlingUnit",
                "<cac:AdditionalDocumentReference");
        // Llegada antes que partida, como en el esquema UBL (DeliveryAddress, luego Despatch).
        assertThat(hijos(xml, "Delivery")).containsExactly("DeliveryAddress", "Despatch");
        assertThat(hijos(xml, "Shipment")).containsExactly("ID", "HandlingCode", "GrossWeightMeasure",
                "TotalTransportHandlingUnitQuantity", "SpecialInstructions", "ShipmentStage", "Delivery");
        assertThat(hijos(xml, "DespatchLine")).containsExactly("ID", "DeliveredQuantity", "OrderLineReference", "Item");
        assertThat(GeneradorGuiaXml.nombreArchivo(traslado(), empresa())).isEqualTo("20000000001-09-T001-7");
    }

    @Test
    void ventaConTransportePublicoReferenciaLaFactura() throws Exception {
        GuiaRemision venta = traslado();
        venta.setMotivoTraslado(MotivoTraslado.VENTA);
        venta.setModalidadTransporte(ModalidadTransporte.PUBLICO);
        venta.setVehiculoM1l(false);
        venta.setPartidaCodigoLocal(null);
        venta.setLlegadaCodigoLocal(null);
        venta.setDestinatarioTipoDocumento("6");
        venta.setDestinatarioNumeroDocumento("20123456786");
        venta.setDestinatarioNombre("DISTRIBUIDORA TEXTIL DEMO S.A.C.");
        venta.setTransportistaRuc("20100066603");
        venta.setTransportistaRazonSocial("TRANSPORTES DEMO SAC");
        venta.setTransportistaRegistroMtc("15123456CNG");
        Comprobante factura = new Comprobante();
        factura.setTipoComprobante(TipoComprobante.FACTURA);
        factura.setSerie("F001");
        factura.setCorrelativo(4);
        venta.setComprobante(factura);

        String texto = firmador.firmar(generador.generar(venta, empresa())).xml();
        Document xml = parsear(texto);

        assertThat(firmaValida(xml)).isTrue();
        assertThat(cbc(xml, "HandlingCode")).isEqualTo("01");
        assertThat(cbc(xml, "TransportModeCode")).isEqualTo("01");
        assertThat(texto).contains("<cac:AdditionalDocumentReference><cbc:ID>F001-4</cbc:ID>")
                .contains("<cbc:DocumentType>Factura</cbc:DocumentType>")
                .contains("<cbc:CompanyID>15123456CNG</cbc:CompanyID>")
                .doesNotContain("AddressTypeCode", "SpecialInstructions");
        Element tipoDocumento = (Element) xml.getElementsByTagNameNS(NS_CBC, "DocumentTypeCode").item(0);
        assertThat(tipoDocumento.getTextContent()).isEqualTo("01");
        assertThat(tipoDocumento.getAttribute("listURI")).endsWith("catalogo61");
        assertThat(hijos(xml, "ShipmentStage")).containsExactly("TransportModeCode", "TransitPeriod", "CarrierParty");
        // La referencia va antes de la firma y de las partes, como manda el esquema.
        assertThat(hijosDeRaiz(xml)).containsSubsequence("DespatchAdviceTypeCode", "AdditionalDocumentReference",
                "Signature", "DespatchSupplierParty", "DeliveryCustomerParty", "Shipment", "DespatchLine");
    }

    @Test
    void transportePrivadoConConductorYPlaca() throws Exception {
        GuiaRemision g = traslado();
        g.setVehiculoM1l(false);
        g.setConductorTipoDocumento("1");
        g.setConductorNumeroDocumento("45678912");
        g.setConductorNombres("JUAN");
        g.setConductorApellidos("PEREZ ROJAS");
        g.setConductorLicencia("Q45678912");
        g.setVehiculoPlaca("ABC123");

        Document xml = parsear(firmador.firmar(generador.generar(g, empresa())).xml());

        assertThat(hijos(xml, "DriverPerson")).containsExactly("ID", "FirstName", "FamilyName", "JobTitle",
                "IdentityDocumentReference");
        assertThat(cbc(xml, "JobTitle")).isEqualTo("Principal");
        assertThat(hijos(xml, "Shipment")).endsWith("ShipmentStage", "Delivery", "TransportHandlingUnit");
        Element placa = (Element) xml.getElementsByTagNameNS(NS_CAC, "TransportEquipment").item(0);
        assertThat(placa.getTextContent()).isEqualTo("ABC123");
    }

    // ---------- Datos ----------

    private static Empresa empresa() {
        Empresa e = new Empresa();
        e.setRuc("20000000001");
        e.setRazonSocial("EMPRESA DEMO S.A.C.");
        e.setDireccionFiscal("Jr. Gamarra 100, La Victoria");
        e.setUbigeo("150115");
        return e;
    }

    /** Del almacén (0001) a la tienda (0000), en auto (M1), 2 bultos con 10 jeans. */
    private static GuiaRemision traslado() {
        GuiaRemision g = new GuiaRemision();
        g.setSerie("T001");
        g.setCorrelativo(7);
        g.setFechaEmision(OffsetDateTime.of(2026, 10, 22, 9, 0, 0, 0, ZoneOffset.ofHours(-5)));
        g.setFechaTraslado(LocalDate.of(2026, 10, 22));
        g.setMotivoTraslado(MotivoTraslado.TRASLADO_ESTABLECIMIENTOS);
        g.setModalidadTransporte(ModalidadTransporte.PRIVADO);
        g.setVehiculoM1l(true);
        g.setPesoBrutoKg(new BigDecimal("12.5"));
        g.setNumeroBultos(2);
        g.setDestinatarioTipoDocumento("6");
        g.setDestinatarioNumeroDocumento("20000000001");
        g.setDestinatarioNombre("EMPRESA DEMO S.A.C.");
        g.setPartidaUbigeo("150115");
        g.setPartidaDireccion("Av. Aviación 500, La Victoria");
        g.setPartidaCodigoLocal("0001");
        g.setLlegadaUbigeo("150115");
        g.setLlegadaDireccion("Jr. Gamarra 100, La Victoria");
        g.setLlegadaCodigoLocal("0000");
        GuiaRemisionDetalle d = new GuiaRemisionDetalle();
        d.setCodigo("MOM01-AZU-28");
        d.setDescripcion("Jean Mom Fit Clásico - Azul talla 28");
        d.setUnidadMedida("NIU");
        d.setCantidad(10);
        g.agregarDetalle(d);
        return g;
    }

    // ---------- Ayudas ----------

    private static Document parsear(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String cbc(Document xml, String nombre) {
        return xml.getElementsByTagNameNS(NS_CBC, nombre).item(0).getTextContent();
    }

    private static List<String> hijos(Document xml, String elementoCac) {
        return nombres(xml.getElementsByTagNameNS(NS_CAC, elementoCac).item(0));
    }

    private static List<String> hijosDeRaiz(Document xml) {
        return nombres(xml.getDocumentElement());
    }

    private static List<String> nombres(Node padre) {
        List<String> nombres = new ArrayList<>();
        for (Node hijo = padre.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            nombres.add(hijo.getLocalName());
        }
        return nombres;
    }

    private static boolean firmaValida(Document xml) throws Exception {
        var firmas = xml.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        DOMValidateContext contexto = new DOMValidateContext(new KeySelector() {
            @Override
            public KeySelectorResult select(javax.xml.crypto.dsig.keyinfo.KeyInfo keyInfo, Purpose purpose,
                                            javax.xml.crypto.AlgorithmMethod method, javax.xml.crypto.XMLCryptoContext context) {
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
        }, firmas.item(0));
        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto);
    }
}
