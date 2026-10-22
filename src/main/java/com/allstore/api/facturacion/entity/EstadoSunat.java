package com.allstore.api.facturacion.entity;

public enum EstadoSunat {
    /** Aún no se envía, o falló el envío y se reintentará. */
    PENDIENTE,
    /**
     * Solo resúmenes y guías: SUNAT recibió el archivo y entregó un ticket; el resultado se
     * consulta después (SUNAT lo procesa en segundo plano).
     */
    EN_PROCESO,
    /** SUNAT lo aceptó: el comprobante es válido. */
    ACEPTADO,
    /** Aceptado con observaciones (es válido; conviene corregir el origen de la observación). */
    OBSERVADO,
    /** SUNAT lo rechazó: el comprobante no es válido y la venta necesita otro comprobante. */
    RECHAZADO,
    /** No se pudo enviar tras varios intentos (revisar y reenviar manualmente). */
    ERROR
}
