package com.allstore.api.facturacion.config;

/** Entorno de SUNAT al que se envían los comprobantes. */
public enum EntornoSunat {

    /** Pruebas: los comprobantes no tienen validez tributaria. */
    BETA("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService"),
    /** Real: requiere usuario SOL, certificado digital y estar inscrito como emisor electrónico. */
    PRODUCCION("https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService");

    private final String urlFacturacion;

    EntornoSunat(String urlFacturacion) {
        this.urlFacturacion = urlFacturacion;
    }

    public String getUrlFacturacion() {
        return urlFacturacion;
    }
}
