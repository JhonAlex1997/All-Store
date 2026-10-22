package com.allstore.api.facturacion.config;

/** Entorno de SUNAT al que se envían los documentos. */
public enum EntornoSunat {

    /**
     * Pruebas: los comprobantes no tienen validez tributaria. SUNAT no ofrece entorno de pruebas
     * para la guía de remisión en su formato vigente (2022): sus URL quedan sin definir.
     */
    BETA("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService", null, null),
    /** Real: requiere usuario SOL, certificado digital y estar inscrito como emisor electrónico. */
    PRODUCCION("https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService",
            "https://api-seguridad.sunat.gob.pe/v1", "https://api-cpe.sunat.gob.pe/v1");

    private final String urlFacturacion;
    private final String urlGreSeguridad;
    private final String urlGreApi;

    EntornoSunat(String urlFacturacion, String urlGreSeguridad, String urlGreApi) {
        this.urlFacturacion = urlFacturacion;
        this.urlGreSeguridad = urlGreSeguridad;
        this.urlGreApi = urlGreApi;
    }

    public String getUrlFacturacion() {
        return urlFacturacion;
    }

    /** API REST de guías: servicio que entrega el token OAuth. */
    public String getUrlGreSeguridad() {
        return urlGreSeguridad;
    }

    /** API REST de guías: envío y consulta de tickets. */
    public String getUrlGreApi() {
        return urlGreApi;
    }
}
