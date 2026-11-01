package com.allstore.api.contabilidad.service;

import com.allstore.api.venta.entity.MetodoPago;

/**
 * Cuentas del Plan Contable General Empresarial (PCGE) que usan los asientos automáticos. Conviene
 * que el contador las revise y, si usa otras subcuentas, se cambien aquí.
 */
public final class PlanCuentas {

    public record Cuenta(String codigo, String denominacion) {
    }

    public static final Cuenta CAJA = new Cuenta("1011", "Caja");
    public static final Cuenta BANCOS = new Cuenta("1041", "Cuentas corrientes operativas");
    public static final Cuenta IGV = new Cuenta("40111", "IGV - Cuenta propia");
    public static final Cuenta PROVEEDORES = new Cuenta("4212", "Facturas, boletas y otros comprobantes por pagar - Emitidas");
    public static final Cuenta VENTAS = new Cuenta("70111", "Mercaderías manufacturadas - Terceros");
    public static final Cuenta COSTO_VENTAS = new Cuenta("69111", "Costo de ventas - Mercaderías manufacturadas");
    public static final Cuenta MERCADERIAS = new Cuenta("20111", "Mercaderías manufacturadas - Costo");
    public static final Cuenta PRODUCTOS_TERMINADOS = new Cuenta("21111", "Productos manufacturados - Costo");
    public static final Cuenta MATERIAS_PRIMAS = new Cuenta("24111", "Materias primas para productos manufacturados");
    public static final Cuenta COMPRA_MERCADERIAS = new Cuenta("60111", "Compra de mercaderías manufacturadas");
    public static final Cuenta COMPRA_MATERIAS_PRIMAS = new Cuenta("60211", "Compra de materias primas para productos manufacturados");
    public static final Cuenta SERVICIOS = new Cuenta("6391", "Otros servicios prestados por terceros");
    public static final Cuenta VARIACION_MERCADERIAS = new Cuenta("61111", "Variación de existencias - Mercaderías manufacturadas");
    public static final Cuenta VARIACION_MATERIAS_PRIMAS = new Cuenta("61211", "Variación de existencias - Materias primas");
    public static final Cuenta VARIACION_PRODUCTOS = new Cuenta("71111", "Variación de la producción almacenada - Productos manufacturados");

    private PlanCuentas() {
    }

    /** Efectivo a caja; tarjeta, Yape, Plin y transferencia llegan a la cuenta del banco. */
    public static Cuenta cobro(MetodoPago metodo) {
        return metodo == MetodoPago.EFECTIVO ? CAJA : BANCOS;
    }
}
