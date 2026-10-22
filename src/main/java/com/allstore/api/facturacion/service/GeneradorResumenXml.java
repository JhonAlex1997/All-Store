package com.allstore.api.facturacion.service;

import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.entity.ResumenSunat;
import com.allstore.api.facturacion.entity.ResumenSunatDetalle;
import com.allstore.api.facturacion.entity.TipoResumen;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.service.CalculoIgv;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;

import static com.allstore.api.facturacion.service.XmlUbl.cac;
import static com.allstore.api.facturacion.service.XmlUbl.cbc;
import static com.allstore.api.facturacion.service.XmlUbl.dosDecimales;
import static com.allstore.api.facturacion.service.XmlUbl.firma;
import static com.allstore.api.facturacion.service.XmlUbl.monto;
import static com.allstore.api.facturacion.service.XmlUbl.raiz;
import static com.allstore.api.facturacion.service.XmlUbl.sac;

/**
 * XML de la comunicación de baja (VoidedDocuments) y del resumen diario (SummaryDocuments). Son
 * formatos UBL 2.0 con extensiones de SUNAT (prefijo sac). Como en el XML de los comprobantes, el
 * orden de los elementos es el del esquema.
 */
@Component
public class GeneradorResumenXml {

    static final String NS_VOIDED = "urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1";
    static final String NS_SUMMARY = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";

    /** Estado de una línea del resumen diario: 1 adicionar, 2 modificar, 3 anulado. */
    private static final String ESTADO_ANULADO = "3";

    public Document generar(ResumenSunat r, Empresa empresa) {
        boolean baja = r.getTipo() == TipoResumen.RA;
        Element raiz = raiz(baja ? NS_VOIDED : NS_SUMMARY, baja ? "VoidedDocuments" : "SummaryDocuments", true);
        cbc(raiz, "UBLVersionID", "2.0");
        cbc(raiz, "CustomizationID", baja ? "1.0" : "1.1");
        cbc(raiz, "ID", r.getNumero());
        // ReferenceDate: emisión de los comprobantes; IssueDate: generación de este documento.
        cbc(raiz, "ReferenceDate", r.getFechaReferencia().toString());
        cbc(raiz, "IssueDate", r.getFechaGeneracion().toString());
        firma(raiz, empresa);

        Element emisor = cac(raiz, "AccountingSupplierParty");
        cbc(emisor, "CustomerAssignedAccountID", empresa.getRuc());
        cbc(emisor, "AdditionalAccountID", "6");
        cbc(cac(cac(emisor, "Party"), "PartyLegalEntity"), "RegistrationName", empresa.getRazonSocial());

        int numero = 1;
        for (ResumenSunatDetalle d : r.getDetalles()) {
            if (baja) {
                lineaBaja(raiz, d, numero++);
            } else {
                lineaResumen(raiz, d.getComprobante(), numero++);
            }
        }
        return raiz.getOwnerDocument();
    }

    /** RUC-RA-20261022-1 (sin extensión). */
    public static String nombreArchivo(ResumenSunat r, Empresa empresa) {
        return empresa.getRuc() + "-" + r.getNumero();
    }

    /** Comprobante que se da de baja: tipo, serie, número y motivo. */
    private void lineaBaja(Element raiz, ResumenSunatDetalle d, int numero) {
        Comprobante c = d.getComprobante();
        Element linea = sac(raiz, "VoidedDocumentsLine");
        cbc(linea, "LineID", String.valueOf(numero));
        cbc(linea, "DocumentTypeCode", c.getTipoComprobante().getCodigo());
        sac(linea, "DocumentSerialID", c.getSerie());
        sac(linea, "DocumentNumberID", String.valueOf(c.getCorrelativo()));
        sac(linea, "VoidReasonDescription", d.getMotivo());
    }

    /** Boleta o nota de boleta en estado "anulado", con su cliente y sus montos como se emitió. */
    private void lineaResumen(Element raiz, Comprobante c, int numero) {
        Element linea = sac(raiz, "SummaryDocumentsLine");
        cbc(linea, "LineID", String.valueOf(numero));
        cbc(linea, "DocumentTypeCode", c.getTipoComprobante().getCodigo());
        cbc(linea, "ID", GeneradorXmlUbl.numero(c));
        Element cliente = cac(linea, "AccountingCustomerParty");
        cbc(cliente, "CustomerAssignedAccountID", c.getClienteNumeroDocumento());
        cbc(cliente, "AdditionalAccountID", c.getClienteTipoDocumento());
        if (esNota(c)) {
            // La nota informa la boleta que modifica.
            Comprobante origen = c.getComprobanteOrigen();
            Element referencia = cac(cac(linea, "BillingReference"), "InvoiceDocumentReference");
            cbc(referencia, "ID", GeneradorXmlUbl.numero(origen));
            cbc(referencia, "DocumentTypeCode", origen.getTipoComprobante().getCodigo());
        }
        cbc(cac(linea, "Status"), "ConditionCode", ESTADO_ANULADO);
        sac(linea, "TotalAmount", dosDecimales(c.getTotal())).setAttribute("currencyID", XmlUbl.MONEDA);
        // Base por tipo de operación: 01 gravada, 02 exonerada, 03 inafecta.
        pago(linea, c.getOpGravadas(), "01");
        pago(linea, c.getOpExoneradas(), "02");
        pago(linea, c.getOpInafectas(), "03");

        Element impuestos = cac(linea, "TaxTotal");
        monto(impuestos, "TaxAmount", c.getTotalIgv());
        Element subtotal = cac(impuestos, "TaxSubtotal");
        monto(subtotal, "TaxAmount", c.getTotalIgv());
        Element categoria = cac(subtotal, "TaxCategory");
        // Tasa del IGV: obligatoria (sin ella SUNAT rechaza con 2992).
        cbc(categoria, "Percent", CalculoIgv.TASA_IGV.movePointRight(2).stripTrailingZeros().toPlainString());
        Element esquema = cac(categoria, "TaxScheme");
        cbc(esquema, "ID", "1000");
        cbc(esquema, "Name", "IGV");
        cbc(esquema, "TaxTypeCode", "VAT");
    }

    private static void pago(Element linea, BigDecimal monto, String tipoOperacion) {
        if (monto.signum() > 0) {
            Element pago = sac(linea, "BillingPayment");
            monto(pago, "PaidAmount", monto);
            cbc(pago, "InstructionID", tipoOperacion);
        }
    }

    private static boolean esNota(Comprobante c) {
        return c.getTipoComprobante() == TipoComprobante.NOTA_CREDITO
                || c.getTipoComprobante() == TipoComprobante.NOTA_DEBITO;
    }
}
