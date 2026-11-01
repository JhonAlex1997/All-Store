package com.allstore.api.inventario.entity;

/**
 * Por qué entra o sale mercadería. Cada motivo fija si es entrada o salida, y si un usuario lo
 * puede registrar a mano o solo lo genera el sistema (ventas, traslados).
 */
public enum MotivoMovimiento {

    INVENTARIO_INICIAL(TipoMovimiento.ENTRADA, true, "Inventario inicial"),
    AJUSTE_ENTRADA(TipoMovimiento.ENTRADA, true, "Ajuste por sobrante"),
    AJUSTE_SALIDA(TipoMovimiento.SALIDA, true, "Ajuste por faltante, merma o prenda dañada"),
    TRASLADO_ENTRADA(TipoMovimiento.ENTRADA, false, "Ingreso por traslado"),
    TRASLADO_SALIDA(TipoMovimiento.SALIDA, false, "Salida por traslado"),
    VENTA(TipoMovimiento.SALIDA, false, "Venta"),
    ANULACION_VENTA(TipoMovimiento.ENTRADA, false, "Anulación de venta"),
    DEVOLUCION_VENTA(TipoMovimiento.ENTRADA, false, "Devolución por nota de crédito"),
    ANULACION_DEVOLUCION(TipoMovimiento.SALIDA, false, "Anulación de nota de crédito"),
    COMPRA(TipoMovimiento.ENTRADA, false, "Compra a proveedor"),
    ANULACION_COMPRA(TipoMovimiento.SALIDA, false, "Anulación de compra"),
    PRODUCCION(TipoMovimiento.ENTRADA, false, "Ingreso de producción terminada"),
    ANULACION_PRODUCCION(TipoMovimiento.SALIDA, false, "Anulación de ingreso de producción");

    private final TipoMovimiento tipo;
    private final boolean manual;
    private final String descripcion;

    MotivoMovimiento(TipoMovimiento tipo, boolean manual, String descripcion) {
        this.tipo = tipo;
        this.manual = manual;
        this.descripcion = descripcion;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    /** Si se puede registrar desde el endpoint de movimientos manuales. */
    public boolean isManual() {
        return manual;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
