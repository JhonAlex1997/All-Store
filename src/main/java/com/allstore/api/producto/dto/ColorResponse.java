package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Color;

public record ColorResponse(Long id, String nombre, String codigo, boolean activo) {

    public static ColorResponse from(Color c) {
        return new ColorResponse(c.getId(), c.getNombre(), c.getCodigo(), c.isActivo());
    }
}
