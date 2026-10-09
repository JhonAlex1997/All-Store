package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Producto;

import java.math.BigDecimal;

/** Fila del listado de productos (sin variantes, para que la lista sea liviana). */
public record ProductoResumenResponse(
        Long id,
        String codigo,
        String nombre,
        Long categoriaId,
        String categoria,
        BigDecimal precioVenta,
        boolean activo
) {
    public static ProductoResumenResponse from(Producto p) {
        return new ProductoResumenResponse(p.getId(), p.getCodigo(), p.getNombre(),
                p.getCategoria().getId(), p.getCategoria().getNombre(), p.getPrecioVenta(), p.isActivo());
    }
}
