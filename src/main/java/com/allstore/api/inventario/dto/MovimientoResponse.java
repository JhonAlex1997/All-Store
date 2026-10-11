package com.allstore.api.inventario.dto;

import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.entity.TipoMovimiento;

import java.time.OffsetDateTime;

/** Línea del kardex. */
public record MovimientoResponse(
        Long id,
        OffsetDateTime fecha,
        String usuario,
        Long varianteId,
        String sku,
        Long establecimientoId,
        String establecimiento,
        TipoMovimiento tipo,
        MotivoMovimiento motivo,
        int cantidad,
        int saldo,
        String documento,
        String observacion
) {
    public static MovimientoResponse from(MovimientoInventario m) {
        return new MovimientoResponse(m.getId(), m.getCreatedAt(), m.getCreatedBy(), m.getVariante().getId(),
                m.getVariante().getSku(), m.getEstablecimiento().getId(), m.getEstablecimiento().getNombre(),
                m.getTipo(), m.getMotivo(), m.getCantidad(), m.getSaldo(), m.getDocumento(), m.getObservacion());
    }
}
