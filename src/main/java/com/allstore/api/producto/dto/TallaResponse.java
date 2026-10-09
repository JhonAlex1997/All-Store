package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Talla;

public record TallaResponse(Long id, String codigo, Integer orden, boolean activo) {

    public static TallaResponse from(Talla t) {
        return new TallaResponse(t.getId(), t.getCodigo(), t.getOrden(), t.isActivo());
    }
}
