package com.allstore.api.reporte.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Una línea de comprobante con todo su contexto: el conjunto de datos para análisis (Excel, Python).
 *
 * @param vigente si cuenta en las ventas (EMITIDO); los anulados, convertidos y reemplazados se
 *                incluyen para tener la historia completa, pero no suman
 * @param signo   1 en ventas y notas de débito, -1 en notas de crédito: cantidad x signo da las
 *                unidades netas y total x signo el importe neto
 */
public record DetalleVentaFila(
        Long comprobanteId,
        OffsetDateTime fecha,
        String tipo,
        String documento,
        String estado,
        boolean vigente,
        int signo,
        String establecimiento,
        String vendedor,
        String clienteTipoDocumento,
        String clienteDocumento,
        String clienteNombre,
        String sku,
        String producto,
        String categoria,
        String talla,
        String color,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal descuento,
        BigDecimal valorVenta,
        BigDecimal igv,
        BigDecimal total,
        boolean porMayor,
        String metodosPago
) {
}
