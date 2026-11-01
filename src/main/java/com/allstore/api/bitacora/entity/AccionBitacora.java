package com.allstore.api.bitacora.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Acciones que quedan en la bitácora: las que mueven dinero o mercadería fuera de lo normal, o cambian accesos. */
@Getter
@RequiredArgsConstructor
public enum AccionBitacora {

    // Ventas
    COMPROBANTE_ANULADO("Anuló un comprobante"),
    COMPROBANTE_REEMITIDO("Reemitió un comprobante rechazado por SUNAT"),
    NOTA_CREDITO_EMITIDA("Emitió una nota de crédito (devolución)"),
    DESCUENTO_EN_VENTA("Vendió con descuento"),
    POR_MAYOR_MANUAL("Dio precio por mayor sin llegar al mínimo"),
    XML_REGENERADO("Regeneró el XML de un comprobante"),

    // Caja
    MOVIMIENTO_CAJA("Registró un ingreso o egreso de efectivo"),
    CAJA_CON_DIFERENCIA("Cerró la caja con diferencia"),

    // Inventario y catálogo
    AJUSTE_INVENTARIO("Ajustó el stock a mano"),
    CARGA_MASIVA("Cargó catálogo y stock desde Excel"),
    PRECIO_CAMBIADO("Cambió un precio"),

    // Usuarios y accesos
    USUARIO_CREADO("Creó un usuario"),
    USUARIO_MODIFICADO("Cambió el rol o el estado de un usuario"),
    PASSWORD_REINICIADA("Reinició la contraseña de un usuario"),
    USUARIO_DESBLOQUEADO("Desbloqueó a un usuario"),
    USUARIO_BLOQUEADO("Usuario bloqueado por intentos fallidos"),

    // Configuración del emisor
    EMPRESA_MODIFICADA("Cambió los datos de la empresa"),
    SERIE_MODIFICADA("Cambió una serie de numeración");

    private final String descripcion;
}
