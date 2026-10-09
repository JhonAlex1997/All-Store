package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.List;

public record ProductoDetalleResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        Long categoriaId,
        String categoria,
        BigDecimal precioVenta,
        String unidadMedida,
        String tipoAfectacionIgv,
        boolean activo,
        List<VarianteResponse> variantes
) {
    public static ProductoDetalleResponse from(Producto p) {
        return new ProductoDetalleResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombre(),
                p.getDescripcion(),
                p.getCategoria().getId(),
                p.getCategoria().getNombre(),
                p.getPrecioVenta(),
                p.getUnidadMedida(),
                p.getTipoAfectacionIgv(),
                p.isActivo(),
                p.getVariantes().stream().map(VarianteResponse::from).toList()
        );
    }
}
