package com.allstore.api.insumo.entity;

/** Por qué entra o sale un insumo. */
public enum MotivoInsumo {
    INVENTARIO_INICIAL(true, true),
    AJUSTE_ENTRADA(true, true),
    AJUSTE_SALIDA(false, true),
    COMPRA(true, false),
    ANULACION_COMPRA(false, false),
    CONSUMO_PRODUCCION(false, false),
    DEVOLUCION_PRODUCCION(true, false);

    private final boolean entrada;
    private final boolean manual;

    MotivoInsumo(boolean entrada, boolean manual) {
        this.entrada = entrada;
        this.manual = manual;
    }

    public boolean isEntrada() {
        return entrada;
    }

    /** Lo puede registrar un usuario como ajuste; los demás los genera el sistema. */
    public boolean isManual() {
        return manual;
    }
}
