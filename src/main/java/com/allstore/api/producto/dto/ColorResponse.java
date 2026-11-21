package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Color;

public record ColorResponse(Long id, String nombre, String codigo, boolean activo, /** Para editar: mandarla en If-Match al guardar. */ long version) {

    public static ColorResponse from(Color c) {
        return new ColorResponse(c.getId(), c.getNombre(), c.getCodigo(), c.isActivo(), c.getVersion());
    }
}
