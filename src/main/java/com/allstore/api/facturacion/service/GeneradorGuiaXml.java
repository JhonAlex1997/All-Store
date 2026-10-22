package com.allstore.api.facturacion.service;

import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.entity.Empresa;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.guia.entity.GuiaRemision;
import com.allstore.api.guia.entity.GuiaRemisionDetalle;
import com.allstore.api.guia.entity.ModalidadTransporte;
import com.allstore.api.venta.entity.Comprobante;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static com.allstore.api.facturacion.service.XmlUbl.cac;
import static com.allstore.api.facturacion.service.XmlUbl.cbc;
import static com.allstore.api.facturacion.service.XmlUbl.firma;
import static com.allstore.api.facturacion.service.XmlUbl.raiz;

/**
 * XML de la guía de remisión remitente electrónica (DespatchAdvice UBL 2.1, formato GRE 2022 de
 * SUNAT). El orden de los elementos es el del esquema UBL; los atributos de catálogo (listURI,
 * schemeURI...) son los que pide la guía de elaboración de SUNAT.
 */
@Component
public class GeneradorGuiaXml {

    static final String NS_DESPATCH = "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2";
    /** Indicador de traslado en vehículo de categoría M1 o L (no se informan conductor ni placa). */
    static final String INDICADOR_M1L = "SUNAT_Envio_IndicadorTrasladoVehiculoM1L";

    private static final String CATALOGOS = "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Document generar(GuiaRemision g, Empresa empresa) {
        Element raiz = raiz(NS_DESPATCH, "DespatchAdvice", false);
        OffsetDateTime fecha = g.getFechaEmision().atZoneSameInstant(JpaAuditingConfig.ZONA_LIMA).toOffsetDateTime();
        cbc(raiz, "UBLVersionID", "2.1");
        cbc(raiz, "CustomizationID", "2.0");
        cbc(raiz, "ID", g.getNumero());
        cbc(raiz, "IssueDate", fecha.toLocalDate().toString());
        cbc(raiz, "IssueTime", HORA.format(fecha));
        catalogo(cbc(raiz, "DespatchAdviceTypeCode", TipoComprobante.GUIA_REMISION_REMITENTE.getCodigo()),
                "Tipo de Documento", "01");
        if (g.getObservacion() != null) {
            cbc(raiz, "Note", g.getObservacion());
        }
        if (g.getComprobante() != null) {
            documentoRelacionado(raiz, g.getComprobante(), empresa);
        }
        firma(raiz, empresa);
        parte(cac(raiz, "DespatchSupplierParty"), "6", empresa.getRuc(), empresa.getRazonSocial());
        parte(cac(raiz, "DeliveryCustomerParty"), g.getDestinatarioTipoDocumento(), g.getDestinatarioNumeroDocumento(),
                g.getDestinatarioNombre());
        envio(raiz, g, empresa);
        for (GuiaRemisionDetalle d : g.getDetalles()) {
            linea(raiz, d);
        }
        return raiz.getOwnerDocument();
    }

    /** RUC-09-T001-15 (sin extensión). */
    public static String nombreArchivo(GuiaRemision g, Empresa empresa) {
        return empresa.getRuc() + "-" + TipoComprobante.GUIA_REMISION_REMITENTE.getCodigo() + "-" + g.getNumero();
    }

    /** Boleta o factura que sustenta el traslado (Catálogo 61: 01 factura, 03 boleta). */
    private void documentoRelacionado(Element raiz, Comprobante c, Empresa empresa) {
        Element referencia = cac(raiz, "AdditionalDocumentReference");
        cbc(referencia, "ID", GeneradorXmlUbl.numero(c));
        catalogo(cbc(referencia, "DocumentTypeCode", c.getTipoComprobante().getCodigo()),
                "Documento relacionado al transporte", "61");
        cbc(referencia, "DocumentType", c.getTipoComprobante().getDescripcion());
        documento(cbc(cac(cac(referencia, "IssuerParty"), "PartyIdentification"), "ID", empresa.getRuc()), "6");
    }

    private void parte(Element contenedor, String tipoDocumento, String numero, String nombre) {
        Element parte = cac(contenedor, "Party");
        documento(cbc(cac(parte, "PartyIdentification"), "ID", numero), tipoDocumento);
        cbc(cac(parte, "PartyLegalEntity"), "RegistrationName", nombre);
    }

    /** Datos del traslado: motivo, peso, modalidad, fecha, transportista o conductor, direcciones y vehículo. */
    private void envio(Element raiz, GuiaRemision g, Empresa empresa) {
        Element envio = cac(raiz, "Shipment");
        cbc(envio, "ID", "SUNAT_Envio");
        // La descripción del motivo (HandlingInstructions) solo la exige el motivo 13 "otros".
        catalogo(cbc(envio, "HandlingCode", g.getMotivoTraslado().getCodigo()), "Motivo de traslado", "20");
        cbc(envio, "GrossWeightMeasure", g.getPesoBrutoKg().setScale(3, RoundingMode.HALF_UP).toPlainString())
                .setAttribute("unitCode", "KGM");
        if (g.getNumeroBultos() != null) {
            cbc(envio, "TotalTransportHandlingUnitQuantity", String.valueOf(g.getNumeroBultos()));
        }
        if (g.isVehiculoM1l()) {
            cbc(envio, "SpecialInstructions", INDICADOR_M1L);
        }

        Element etapa = cac(envio, "ShipmentStage");
        catalogo(cbc(etapa, "TransportModeCode", g.getModalidadTransporte().getCodigo()), "Modalidad de traslado", "18");
        cbc(cac(etapa, "TransitPeriod"), "StartDate", g.getFechaTraslado().toString());
        if (g.getModalidadTransporte() == ModalidadTransporte.PUBLICO) {
            Element transportista = cac(etapa, "CarrierParty");
            documento(cbc(cac(transportista, "PartyIdentification"), "ID", g.getTransportistaRuc()), "6");
            Element legal = cac(transportista, "PartyLegalEntity");
            cbc(legal, "RegistrationName", g.getTransportistaRazonSocial());
            if (g.getTransportistaRegistroMtc() != null) {
                cbc(legal, "CompanyID", g.getTransportistaRegistroMtc());
            }
        } else if (g.tieneConductor()) {
            Element conductor = cac(etapa, "DriverPerson");
            documento(cbc(conductor, "ID", g.getConductorNumeroDocumento()), g.getConductorTipoDocumento());
            cbc(conductor, "FirstName", g.getConductorNombres());
            cbc(conductor, "FamilyName", g.getConductorApellidos());
            cbc(conductor, "JobTitle", "Principal");
            cbc(cac(conductor, "IdentityDocumentReference"), "ID", g.getConductorLicencia());
        }

        Element entrega = cac(envio, "Delivery");
        direccion(cac(entrega, "DeliveryAddress"), g.getLlegadaUbigeo(), g.getLlegadaCodigoLocal(), g.getLlegadaDireccion(), empresa);
        direccion(cac(cac(entrega, "Despatch"), "DespatchAddress"), g.getPartidaUbigeo(), g.getPartidaCodigoLocal(),
                g.getPartidaDireccion(), empresa);
        if (g.getModalidadTransporte() == ModalidadTransporte.PRIVADO && g.getVehiculoPlaca() != null) {
            cbc(cac(cac(envio, "TransportHandlingUnit"), "TransportEquipment"), "ID", g.getVehiculoPlaca());
        }
    }

    /** Ubigeo, código de establecimiento anexo (traslados entre locales) y dirección. */
    private void direccion(Element direccion, String ubigeo, String codigoLocal, String linea, Empresa empresa) {
        Element id = cbc(direccion, "ID", ubigeo);
        id.setAttribute("schemeAgencyName", "PE:INEI");
        id.setAttribute("schemeName", "Ubigeos");
        if (codigoLocal != null) {
            Element anexo = cbc(direccion, "AddressTypeCode", codigoLocal);
            anexo.setAttribute("listID", empresa.getRuc());
            anexo.setAttribute("listAgencyName", "PE:SUNAT");
            anexo.setAttribute("listName", "Establecimientos anexos");
        }
        cbc(cac(direccion, "AddressLine"), "Line", linea);
    }

    private void linea(Element raiz, GuiaRemisionDetalle d) {
        Element linea = cac(raiz, "DespatchLine");
        cbc(linea, "ID", String.valueOf(d.getItem()));
        cbc(linea, "DeliveredQuantity", String.valueOf(d.getCantidad())).setAttribute("unitCode", d.getUnidadMedida());
        cbc(cac(linea, "OrderLineReference"), "LineID", String.valueOf(d.getItem()));
        Element item = cac(linea, "Item");
        cbc(item, "Description", d.getDescripcion());
        cbc(cac(item, "SellersItemIdentification"), "ID", d.getCodigo());
    }

    /** Atributos de un código de catálogo SUNAT. */
    private static void catalogo(Element e, String nombre, String numeroCatalogo) {
        e.setAttribute("listAgencyName", "PE:SUNAT");
        e.setAttribute("listName", nombre);
        e.setAttribute("listURI", CATALOGOS + numeroCatalogo);
    }

    /** Atributos de un documento de identidad (Catálogo 06). */
    private static void documento(Element e, String tipo) {
        e.setAttribute("schemeID", tipo);
        e.setAttribute("schemeName", "Documento de Identidad");
        e.setAttribute("schemeAgencyName", "PE:SUNAT");
        e.setAttribute("schemeURI", CATALOGOS + "06");
    }
}
