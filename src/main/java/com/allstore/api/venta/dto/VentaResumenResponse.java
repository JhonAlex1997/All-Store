package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.EstadoComprobante;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Fila del listado de ventas. Usa la copia de datos del cliente, sin unir tablas. */
public record VentaResumenResponse(
        Long id,
        TipoComprobante tipoComprobante,
        String numero,
        OffsetDateTime fechaEmision,
        String clienteDocumento,
        String clienteNombre,
        BigDecimal total,
        EstadoComprobante estado,
        String vendedor
) {
    public static VentaResumenResponse from(Comprobante c) {
        return new VentaResumenResponse(c.getId(), c.getTipoComprobante(), c.getNumeroImpreso(), c.getFechaEmision(),
                c.getClienteNumeroDocumento(), c.getClienteNombre(), c.getTotal(), c.getEstado(), c.getCreatedBy());
    }
}
