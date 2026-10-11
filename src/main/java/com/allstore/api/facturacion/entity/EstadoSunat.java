package com.allstore.api.facturacion.entity;

public enum EstadoSunat {
    /** Aún no se envía, o falló el envío y se reintentará. */
    PENDIENTE,
    /** SUNAT lo aceptó: el comprobante es válido. */
    ACEPTADO,
    /** Aceptado con observaciones (es válido; conviene corregir el origen de la observación). */
    OBSERVADO,
    /** SUNAT lo rechazó: el comprobante no es válido y la venta necesita otro comprobante. */
    RECHAZADO,
    /** No se pudo enviar tras varios intentos (revisar y reenviar manualmente). */
    ERROR
}
