package com.allstore.api.facturacion.entity;

/**
 * Documentos que agrupan comprobantes ya emitidos para informar algo a SUNAT. El sistema los usa
 * para anular: la factura y sus notas con comunicación de baja; la boleta y sus notas con resumen
 * diario en estado "anulado" (así lo exige SUNAT para las boletas).
 */
public enum TipoResumen {

    RA("Comunicación de baja"),
    RC("Resumen diario");

    private final String descripcion;

    TipoResumen(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** RA para las series que empiezan con F (facturas y sus notas); RC para las de boletas (B). */
    public static TipoResumen paraSerie(String serie) {
        return serie.charAt(0) == 'F' ? RA : RC;
    }
}
