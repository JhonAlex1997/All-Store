package com.allstore.api.compra.entity;

/** Catálogo 10 SUNAT de los documentos de compra que se registran. Solo la factura da crédito fiscal (IGV). */
public enum TipoDocumentoCompra {
    FACTURA("01", true),
    BOLETA("03", false),
    RECIBO_HONORARIOS("02", false),
    TICKET("12", false),
    OTRO("00", false);

    private final String codigo;
    private final boolean creditoFiscal;

    TipoDocumentoCompra(String codigo, boolean creditoFiscal) {
        this.codigo = codigo;
        this.creditoFiscal = creditoFiscal;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Si el IGV del documento se puede usar como crédito fiscal (se separa la base y el IGV). */
    public boolean isCreditoFiscal() {
        return creditoFiscal;
    }

    public static TipoDocumentoCompra desdeCodigo(String codigo) {
        for (TipoDocumentoCompra t : values()) {
            if (t.codigo.equals(codigo)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de documento de compra desconocido: " + codigo);
    }
}
