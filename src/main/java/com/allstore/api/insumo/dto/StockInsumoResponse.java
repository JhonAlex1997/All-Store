package com.allstore.api.insumo.dto;

import com.allstore.api.insumo.entity.StockInsumo;

import java.math.BigDecimal;

public record StockInsumoResponse(Long insumoId, String codigo, String nombre, String unidadMedida, Long establecimientoId,
                                  String establecimiento, BigDecimal cantidad) {

    public static StockInsumoResponse from(StockInsumo s) {
        return new StockInsumoResponse(s.getInsumo().getId(), s.getInsumo().getCodigo(), s.getInsumo().getNombre(),
                s.getInsumo().getUnidadMedida(), s.getEstablecimiento().getId(), s.getEstablecimiento().getNombre(),
                s.getCantidad());
    }
}
