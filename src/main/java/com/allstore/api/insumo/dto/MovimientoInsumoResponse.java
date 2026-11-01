package com.allstore.api.insumo.dto;

import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.entity.MovimientoInsumo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MovimientoInsumoResponse(Long id, OffsetDateTime fecha, Long insumoId, String insumo, Long establecimientoId,
                                       String tipo, MotivoInsumo motivo, BigDecimal cantidad, BigDecimal saldo,
                                       String documento, String observacion, String usuario) {

    public static MovimientoInsumoResponse from(MovimientoInsumo m) {
        return new MovimientoInsumoResponse(m.getId(), m.getFecha(), m.getInsumo().getId(), m.getInsumo().getNombre(),
                m.getEstablecimientoId(), m.getTipo(), m.getMotivo(), m.getCantidad(), m.getSaldo(), m.getDocumento(),
                m.getObservacion(), m.getUsuario());
    }
}
