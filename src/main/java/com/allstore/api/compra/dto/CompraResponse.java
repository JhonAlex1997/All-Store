package com.allstore.api.compra.dto;

import com.allstore.api.compra.entity.Compra;
import com.allstore.api.compra.entity.CompraDetalle;
import com.allstore.api.compra.entity.TipoDocumentoCompra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CompraResponse(Long id, Long proveedorId, String proveedor, String proveedorDocumento, Long establecimientoId,
                             String establecimiento, TipoDocumentoCompra tipoDocumento, String documento, LocalDate fechaEmision,
                             String periodo, BigDecimal baseGravada, BigDecimal igv, BigDecimal noGravado, BigDecimal total,
                             String estado, String motivoAnulacion, String observacion, List<Linea> lineas) {

    /** @param tipo PRENDA, INSUMO o SERVICIO */
    public record Linea(int item, String tipo, Long varianteId, Long insumoId, String descripcion, BigDecimal cantidad,
                        BigDecimal costoUnitario, boolean gravado, BigDecimal valor, BigDecimal igv, BigDecimal total) {

        static Linea from(CompraDetalle d) {
            String tipo = d.getVariante() != null ? "PRENDA" : d.getInsumo() != null ? "INSUMO" : "SERVICIO";
            return new Linea(d.getItem(), tipo, d.getVariante() != null ? d.getVariante().getId() : null,
                    d.getInsumo() != null ? d.getInsumo().getId() : null, d.getDescripcion(), d.getCantidad(), d.getCostoUnitario(),
                    d.isGravado(), d.getValor(), d.getIgv(), d.getTotal());
        }
    }

    public static CompraResponse from(Compra c, boolean conLineas) {
        return new CompraResponse(c.getId(), c.getProveedor().getId(), c.getProveedor().getRazonSocial(),
                c.getProveedor().getNumeroDocumento(), c.getEstablecimiento().getId(), c.getEstablecimiento().getNombre(),
                c.getTipoDocumento(), c.getDocumento(), c.getFechaEmision(), c.getPeriodo(), c.getBaseGravada(), c.getIgv(),
                c.getNoGravado(), c.getTotal(), c.getEstado(), c.getMotivoAnulacion(), c.getObservacion(),
                conLineas ? c.getDetalles().stream().map(Linea::from).toList() : List.of());
    }
}
