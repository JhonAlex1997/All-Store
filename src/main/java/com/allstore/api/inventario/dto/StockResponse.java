package com.allstore.api.inventario.dto;

import com.allstore.api.inventario.entity.Stock;
import com.allstore.api.producto.entity.ProductoVariante;

public record StockResponse(
        Long varianteId,
        String sku,
        String codigoBarras,
        String producto,
        String talla,
        String color,
        Long establecimientoId,
        String establecimiento,
        int cantidad
) {
    public static StockResponse from(Stock s) {
        ProductoVariante v = s.getVariante();
        return new StockResponse(v.getId(), v.getSku(), v.getCodigoBarras(), v.getProducto().getNombre(),
                v.getTalla().getCodigo(), v.getColor().getNombre(), s.getEstablecimiento().getId(),
                s.getEstablecimiento().getNombre(), s.getCantidad());
    }
}
