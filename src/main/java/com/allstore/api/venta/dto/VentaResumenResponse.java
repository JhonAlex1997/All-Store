package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fila del listado de ventas. Usa la copia de datos del cliente, sin unir tablas.
 *
 * @param metodosPago métodos con que se pagó (o se devolvió, en una nota de crédito), sin repetir
 * @param estadoSunat null en las notas de venta, que no se envían
 */
public record VentaResumenResponse(
        Long id,
        TipoComprobante tipoComprobante,
        String numero,
        OffsetDateTime fechaEmision,
        String clienteDocumento,
        String clienteNombre,
        BigDecimal total,
        EstadoComprobante estado,
        String vendedor,
        List<MetodoPago> metodosPago,
        boolean porMayor,
        boolean porMayorManual,
        EstadoSunat estadoSunat
) {
    public static VentaResumenResponse from(Comprobante c, List<MetodoPago> metodosPago, EstadoSunat estadoSunat) {
        return new VentaResumenResponse(c.getId(), c.getTipoComprobante(), c.getNumeroImpreso(), c.getFechaEmision(),
                c.getClienteNumeroDocumento(), c.getClienteNombre(), c.getTotal(), c.getEstado(), c.getCreatedBy(),
                metodosPago, c.isPorMayor(), c.isPorMayorManual(), estadoSunat);
    }
}
