package com.allstore.api.venta.entity;

import java.util.Arrays;

/**
 * Motivos de nota de crédito del Catálogo 09 de SUNAT que aplican a la tienda. Todos devuelven
 * mercadería al stock. Los "totales" revierten el comprobante completo; la devolución por ítem
 * solo las prendas y cantidades indicadas.
 *
 * <p>Los motivos por rebaja de precio sin devolución (descuentos posteriores, bonificaciones,
 * disminución de valor) se agregarán cuando se necesiten.
 */
public enum MotivoNotaCredito {

    ANULACION_OPERACION("01", "Anulación de la operación", true),
    ANULACION_ERROR_RUC("02", "Anulación por error en el RUC", true),
    DEVOLUCION_TOTAL("06", "Devolución total", true),
    DEVOLUCION_POR_ITEM("07", "Devolución por ítem", false);

    private final String codigo;
    private final String descripcion;
    private final boolean total;

    MotivoNotaCredito(String codigo, String descripcion, boolean total) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.total = total;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Si revierte el comprobante completo (si no, solo los ítems indicados). */
    public boolean isTotal() {
        return total;
    }

    public static MotivoNotaCredito desdeCodigo(String codigo) {
        return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Motivo de nota de crédito desconocido: " + codigo));
    }
}
