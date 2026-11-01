package com.allstore.api.insumo.dto;

import com.allstore.api.insumo.entity.Insumo;

import java.math.BigDecimal;

public record InsumoResponse(Long id, String codigo, String nombre, String unidadMedida, BigDecimal costo, boolean activo) {

    public static InsumoResponse from(Insumo i) {
        return new InsumoResponse(i.getId(), i.getCodigo(), i.getNombre(), i.getUnidadMedida(), i.getCosto(), i.isActivo());
    }
}
