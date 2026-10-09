package com.allstore.api.empresa.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Documentos que emite el sistema. Los electrónicos usan el código del Catálogo 01 de SUNAT;
 * la nota de venta es un documento interno sin validez tributaria (código propio "NV").
 */
public enum TipoComprobante {

    FACTURA("01", "Factura", true),
    BOLETA("03", "Boleta de venta", true),
    NOTA_CREDITO("07", "Nota de crédito", true),
    NOTA_DEBITO("08", "Nota de débito", true),
    GUIA_REMISION_REMITENTE("09", "Guía de remisión remitente", true),
    NOTA_VENTA("NV", "Nota de venta", false);

    private final String codigo;
    private final String descripcion;
    private final boolean electronico;

    TipoComprobante(String codigo, String descripcion, boolean electronico) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.electronico = electronico;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Si se envía a SUNAT. La nota de venta no se envía ni sustenta gasto o crédito fiscal. */
    public boolean isElectronico() {
        return electronico;
    }

    public static TipoComprobante desdeCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(t -> t.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de comprobante desconocido: " + codigo));
    }

    /**
     * Reglas de serie de SUNAT para comprobantes electrónicos: 4 caracteres; la factura empieza
     * con F, la boleta con B, la guía de remisión remitente con T, y las notas de crédito y
     * débito con F si modifican facturas o con B si modifican boletas. La nota de venta no
     * puede empezar con esas letras para no confundirse con un comprobante electrónico.
     *
     * @return el motivo por el que la serie no es válida, o vacío si es correcta
     */
    public Optional<String> validarSerie(String serie) {
        if (serie == null || !serie.matches("[A-Z0-9]{4}")) {
            return Optional.of("La serie debe tener 4 caracteres (letras mayúsculas o números)");
        }
        char inicial = serie.charAt(0);
        boolean valida = switch (this) {
            case FACTURA -> inicial == 'F';
            case BOLETA -> inicial == 'B';
            case NOTA_CREDITO, NOTA_DEBITO -> inicial == 'F' || inicial == 'B';
            case GUIA_REMISION_REMITENTE -> inicial == 'T';
            case NOTA_VENTA -> inicial != 'F' && inicial != 'B' && inicial != 'T';
        };
        if (valida) {
            return Optional.empty();
        }
        return Optional.of(switch (this) {
            case FACTURA -> "La serie de una factura debe empezar con F (ej. F001)";
            case BOLETA -> "La serie de una boleta debe empezar con B (ej. B001)";
            case NOTA_CREDITO, NOTA_DEBITO ->
                    "La serie de una nota debe empezar con F (si modifica facturas) o B (si modifica boletas)";
            case GUIA_REMISION_REMITENTE -> "La serie de una guía de remisión remitente debe empezar con T (ej. T001)";
            case NOTA_VENTA -> "La serie de una nota de venta no puede empezar con F, B ni T (ej. NV01)";
        });
    }
}
