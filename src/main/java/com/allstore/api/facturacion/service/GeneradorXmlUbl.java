package com.allstore.api.facturacion.service;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.service.CalculoIgv;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Arma el XML UBL 2.1 que exige SUNAT para factura y boleta (Invoice) y nota de crédito
 * (CreditNote). El orden de los elementos es el del esquema UBL: SUNAT lo valida y rechaza un
 * XML con los elementos en otro orden.
 *
 * <p>Los montos salen del comprobante tal como se guardó (base, IGV y total por línea), así el
 * XML siempre coincide con lo que se cobró y se imprimió.
 */
@Component
public class GeneradorXmlUbl {

    static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String NS_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";

    private static final String MONEDA = "PEN";
    private static final BigDecimal FACTOR_IGV = BigDecimal.ONE.add(CalculoIgv.TASA_IGV);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Id de la firma: el bloque cac:Signature la referencia y el firmador la crea con este Id. */
    public static final String ID_FIRMA = "SignatureSP";

    public Document generar(Comprobante c, Empresa empresa) {
        boolean notaCredito = c.getTipoComprobante() == TipoComprobante.NOTA_CREDITO;
        Document doc = nuevoDocumento();
        Element raiz = doc.createElementNS(notaCredito ? NS_CREDIT_NOTE : NS_INVOICE, notaCredito ? "CreditNote" : "Invoice");
        doc.appendChild(raiz);
        // Todas las declaraciones de espacio de nombres como atributos explícitos, incluida la
        // principal: la firma se calcula sobre el DOM en memoria, y si el serializador agregara
        // una declaración que no estaba, el XML enviado ya no coincidiría con el firmado
        // (SUNAT lo rechaza con 2335 "Incorrect reference digest value").
        raiz.setAttributeNS(NS_XMLNS, "xmlns", notaCredito ? NS_CREDIT_NOTE : NS_INVOICE);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:cac", NS_CAC);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:cbc", NS_CBC);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:ds", NS_DS);
        raiz.setAttributeNS(NS_XMLNS, "xmlns:ext", NS_EXT);

        // Aquí va la firma digital (la agrega FirmadorXml).
        Element extensiones = hijo(raiz, NS_EXT, "ext:UBLExtensions");
        hijo(hijo(extensiones, NS_EXT, "ext:UBLExtension"), NS_EXT, "ext:ExtensionContent");

        OffsetDateTime fecha = c.getFechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        cbc(raiz, "UBLVersionID", "2.1");
        cbc(raiz, "CustomizationID", "2.0");
        cbc(raiz, "ID", numero(c));
        cbc(raiz, "IssueDate", fecha.toLocalDate().toString());
        cbc(raiz, "IssueTime", HORA.format(fecha));
        if (!notaCredito) {
            // Catálogo 51: 0101 = venta interna.
            cbc(raiz, "InvoiceTypeCode", c.getTipoComprobante().getCodigo()).setAttribute("listID", "0101");
        }
        // Leyenda 1000: importe en letras.
        cbc(raiz, "Note", MontoEnLetras.convertir(c.getTotal())).setAttribute("languageLocaleID", "1000");
        cbc(raiz, "DocumentCurrencyCode", MONEDA);

        if (notaCredito) {
            Comprobante origen = c.getComprobanteOrigen();
            Element discrepancia = cac(raiz, "DiscrepancyResponse");
            cbc(discrepancia, "ReferenceID", numero(origen));
            cbc(discrepancia, "ResponseCode", c.getMotivoNota().getCodigo());
            cbc(discrepancia, "Description", c.getSustento());
            Element referencia = cac(cac(raiz, "BillingReference"), "InvoiceDocumentReference");
            cbc(referencia, "ID", numero(origen));
            cbc(referencia, "DocumentTypeCode", origen.getTipoComprobante().getCodigo());
        }

        firma(raiz, empresa);
        emisor(raiz, empresa);
        receptor(raiz, c);
        if (c.getTipoComprobante() == TipoComprobante.FACTURA) {
            // Forma de pago: obligatoria en facturas. Las ventas de la tienda son al contado.
            Element forma = cac(raiz, "PaymentTerms");
            cbc(forma, "ID", "FormaPago");
            cbc(forma, "PaymentMeansID", "Contado");
        }
        impuestos(raiz, c);
        totales(raiz, c);
        int i = 1;
        for (ComprobanteDetalle d : c.getDetalles()) {
            linea(raiz, d, i++, notaCredito);
        }
        return doc;
    }

    /** Nombre de archivo que exige SUNAT: RUC-TIPO-SERIE-CORRELATIVO. */
    public static String nombreArchivo(Comprobante c, Empresa empresa) {
        return empresa.getRuc() + "-" + c.getTipoComprobante().getCodigo() + "-" + numero(c);
    }

    /** En el XML el correlativo va sin ceros a la izquierda: B001-15. */
    static String numero(Comprobante c) {
        return c.getSerie() + "-" + c.getCorrelativo();
    }

    // ---------- Bloques ----------

    private void firma(Element raiz, Empresa empresa) {
        Element firma = cac(raiz, "Signature");
        cbc(firma, "ID", ID_FIRMA);
        Element firmante = cac(firma, "SignatoryParty");
        cbc(cac(firmante, "PartyIdentification"), "ID", empresa.getRuc());
        cbc(cac(firmante, "PartyName"), "Name", empresa.getRazonSocial());
        cbc(cac(cac(firma, "DigitalSignatureAttachment"), "ExternalReference"), "URI", "#" + ID_FIRMA);
    }

    private void emisor(Element raiz, Empresa empresa) {
        Element parte = cac(cac(raiz, "AccountingSupplierParty"), "Party");
        cbc(cac(parte, "PartyIdentification"), "ID", empresa.getRuc()).setAttribute("schemeID", "6");
        if (empresa.getNombreComercial() != null) {
            cbc(cac(parte, "PartyName"), "Name", empresa.getNombreComercial());
        }
        Element legal = cac(parte, "PartyLegalEntity");
        cbc(legal, "RegistrationName", empresa.getRazonSocial());
        Element direccion = cac(legal, "RegistrationAddress");
        cbc(direccion, "ID", empresa.getUbigeo());
        // Código del establecimiento anexo donde se emite; 0000 = domicilio fiscal.
        cbc(direccion, "AddressTypeCode", "0000");
        cbc(cac(direccion, "AddressLine"), "Line", empresa.getDireccionFiscal());
        cbc(cac(direccion, "Country"), "IdentificationCode", "PE");
    }

    private void receptor(Element raiz, Comprobante c) {
        Element parte = cac(cac(raiz, "AccountingCustomerParty"), "Party");
        cbc(cac(parte, "PartyIdentification"), "ID", c.getClienteNumeroDocumento())
                .setAttribute("schemeID", c.getClienteTipoDocumento());
        cbc(cac(parte, "PartyLegalEntity"), "RegistrationName", c.getClienteNombre());
    }

    /** Total de impuestos del comprobante, separado por tipo de afectación. */
    private void impuestos(Element raiz, Comprobante c) {
        Element total = cac(raiz, "TaxTotal");
        monto(total, "TaxAmount", c.getTotalIgv());
        if (c.getOpGravadas().signum() > 0) {
            subtotal(total, c.getOpGravadas(), c.getTotalIgv(), null, null, Tributo.IGV);
        }
        if (c.getOpExoneradas().signum() > 0) {
            subtotal(total, c.getOpExoneradas(), BigDecimal.ZERO, null, null, Tributo.EXONERADO);
        }
        if (c.getOpInafectas().signum() > 0) {
            subtotal(total, c.getOpInafectas(), BigDecimal.ZERO, null, null, Tributo.INAFECTO);
        }
    }

    private void totales(Element raiz, Comprobante c) {
        Element totales = cac(raiz, "LegalMonetaryTotal");
        BigDecimal valorVenta = c.getOpGravadas().add(c.getOpExoneradas()).add(c.getOpInafectas());
        monto(totales, "LineExtensionAmount", valorVenta);
        monto(totales, "TaxInclusiveAmount", c.getTotal());
        monto(totales, "PayableAmount", c.getTotal());
    }

    private void linea(Element raiz, ComprobanteDetalle d, int numero, boolean notaCredito) {
        Element linea = cac(raiz, notaCredito ? "CreditNoteLine" : "InvoiceLine");
        cbc(linea, "ID", String.valueOf(numero));
        cbc(linea, notaCredito ? "CreditedQuantity" : "InvoicedQuantity", String.valueOf(d.getCantidad()))
                .setAttribute("unitCode", d.getUnidadMedida());
        monto(linea, "LineExtensionAmount", d.getValorVenta());

        // Precio de venta unitario con IGV (tipo 01), ya con el descuento aplicado.
        Element precioReferencia = cac(cac(linea, "PricingReference"), "AlternativeConditionPrice");
        precio(precioReferencia, "PriceAmount", d.getTotal().divide(BigDecimal.valueOf(d.getCantidad()), 10, RoundingMode.HALF_UP));
        cbc(precioReferencia, "PriceTypeCode", "01");

        Tributo tributo = Tributo.de(d.getTipoAfectacionIgv());
        BigDecimal valorUnitario = tributo == Tributo.IGV
                ? d.getPrecioUnitario().divide(FACTOR_IGV, 10, RoundingMode.HALF_UP)
                : d.getPrecioUnitario();
        BigDecimal valorBruto = valorUnitario.multiply(BigDecimal.valueOf(d.getCantidad())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal descuento = valorBruto.subtract(d.getValorVenta());
        boolean conDescuento = d.getDescuento().signum() > 0 && descuento.signum() > 0;

        if (conDescuento && notaCredito) {
            // En notas de crédito SUNAT no resta el descuento de la línea (AllowanceCharge) al
            // validar cantidad x valor unitario = valor de la línea (rechaza con 3271 / observa con
            // 4288). Se informa el valor unitario ya neto del descuento, sin AllowanceCharge.
            valorUnitario = d.getValorVenta().divide(BigDecimal.valueOf(d.getCantidad()), 10, RoundingMode.HALF_UP);
        } else if (conDescuento) {
            descuento(linea, descuento, valorBruto);
        }
        Element impuesto = cac(linea, "TaxTotal");
        monto(impuesto, "TaxAmount", d.getIgv());
        subtotal(impuesto, d.getValorVenta(), d.getIgv(),
                tributo == Tributo.IGV ? CalculoIgv.TASA_IGV.movePointRight(2) : BigDecimal.ZERO,
                d.getTipoAfectacionIgv(), tributo);

        Element item = cac(linea, "Item");
        cbc(item, "Description", d.getDescripcion());
        cbc(cac(item, "SellersItemIdentification"), "ID", d.getSku());
        precio(cac(linea, "Price"), "PriceAmount", valorUnitario);
    }

    /** Descuento de la línea, sin IGV (código 00: descuento que afecta la base imponible). */
    private void descuento(Element linea, BigDecimal descuento, BigDecimal valorBruto) {
        Element cargo = cac(linea, "AllowanceCharge");
        cbc(cargo, "ChargeIndicator", "false");
        cbc(cargo, "AllowanceChargeReasonCode", "00");
        cbc(cargo, "MultiplierFactorNumeric", descuento.divide(valorBruto, 5, RoundingMode.HALF_UP).toPlainString());
        monto(cargo, "Amount", descuento);
        monto(cargo, "BaseAmount", valorBruto);
    }

    private void subtotal(Element padre, BigDecimal base, BigDecimal impuesto, BigDecimal porcentaje,
                          String afectacion, Tributo tributo) {
        Element subtotal = cac(padre, "TaxSubtotal");
        monto(subtotal, "TaxableAmount", base);
        monto(subtotal, "TaxAmount", impuesto);
        Element categoria = cac(subtotal, "TaxCategory");
        if (porcentaje != null) {
            cbc(categoria, "Percent", porcentaje.stripTrailingZeros().toPlainString());
        }
        if (afectacion != null) {
            // Catálogo 07: tipo de afectación del IGV.
            cbc(categoria, "TaxExemptionReasonCode", afectacion);
        }
        Element esquema = cac(categoria, "TaxScheme");
        cbc(esquema, "ID", tributo.codigo);
        cbc(esquema, "Name", tributo.nombre);
        cbc(esquema, "TaxTypeCode", tributo.tipo);
    }

    /** Catálogo 05: tributos. */
    private enum Tributo {
        IGV("1000", "IGV", "VAT"),
        EXONERADO("9997", "EXO", "VAT"),
        INAFECTO("9998", "INA", "FRE");

        final String codigo;
        final String nombre;
        final String tipo;

        Tributo(String codigo, String nombre, String tipo) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.tipo = tipo;
        }

        /** 10-17 gravado, 20-21 exonerado, 30-37 inafecto. */
        static Tributo de(String afectacion) {
            return switch (afectacion.charAt(0)) {
                case '1' -> IGV;
                case '2' -> EXONERADO;
                default -> INAFECTO;
            };
        }
    }

    // ---------- Utilidades DOM ----------

    private static Document nuevoDocumento() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("No se pudo crear el documento XML", e);
        }
    }

    private static Element hijo(Element padre, String ns, String nombre) {
        Element e = padre.getOwnerDocument().createElementNS(ns, nombre);
        padre.appendChild(e);
        return e;
    }

    private static Element cac(Element padre, String nombre) {
        return hijo(padre, NS_CAC, "cac:" + nombre);
    }

    private static Element cbc(Element padre, String nombre, String valor) {
        Element e = hijo(padre, NS_CBC, "cbc:" + nombre);
        e.setTextContent(valor);
        return e;
    }

    /** Importe con 2 decimales y moneda. */
    private static void monto(Element padre, String nombre, BigDecimal valor) {
        cbc(padre, nombre, valor.setScale(2, RoundingMode.HALF_UP).toPlainString()).setAttribute("currencyID", MONEDA);
    }

    /** Precio unitario: SUNAT admite hasta 10 decimales; se quitan los ceros sobrantes (mínimo 2). */
    private static void precio(Element padre, String nombre, BigDecimal valor) {
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() < 2) {
            limpio = limpio.setScale(2);
        }
        cbc(padre, nombre, limpio.toPlainString()).setAttribute("currencyID", MONEDA);
    }
}
