package com.allstore.api.venta.entity;

import java.util.Arrays;

/**
 * Motivos de nota de débito del Catálogo 10 de SUNAT que aplican a la tienda. Ninguno mueve
 * mercadería: la nota aumenta lo que el cliente paga por un comprobante ya emitido.
 */
public enum MotivoNotaDebito {

    INTERESES_MORA("01", "Intereses por mora"),
    AUMENTO_VALOR("02", "Aumento en el valor"),
    PENALIDADES("03", "Penalidades / otros conceptos");

    private final String codigo;
    private final String descripcion;

    MotivoNotaDebito(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static MotivoNotaDebito desdeCodigo(String codigo) {
        return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Motivo de nota de débito desconocido: " + codigo));
    }
}
