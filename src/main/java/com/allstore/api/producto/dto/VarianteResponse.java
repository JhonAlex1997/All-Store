package com.allstore.api.producto.dto;

import com.allstore.api.producto.entity.ProductoVariante;

import java.math.BigDecimal;

/**
 * @param precioVenta    precio propio de la variante, o null si usa el del producto
 * @param precioEfectivo precio al que realmente se vende
 */
public record VarianteResponse(
        Long id,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        String sku,
        String codigoBarras,
        Long tallaId,
        String talla,
        Long colorId,
        String color,
        BigDecimal precioVenta,
        BigDecimal precioEfectivo,
        boolean activo
) {
    public static VarianteResponse from(ProductoVariante v) {
        return new VarianteResponse(
                v.getId(),
                v.getProducto().getId(),
                v.getProducto().getCodigo(),
                v.getProducto().getNombre(),
                v.getSku(),
                v.getCodigoBarras(),
                v.getTalla().getId(),
                v.getTalla().getCodigo(),
                v.getColor().getId(),
                v.getColor().getNombre(),
                v.getPrecioVenta(),
                v.getPrecioEfectivo(),
                v.isActivo()
        );
    }
}
