package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Categoria;

public record CategoriaResponse(Long id, String nombre, boolean activo) {

    public static CategoriaResponse from(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.isActivo());
    }
}
