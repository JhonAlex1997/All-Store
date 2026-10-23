package com.allstore.api.venta.dto;

import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.facturacion.entity.EstadoSunat;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.entity.MetodoPago;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtros del listado y de los totales de ventas; todos opcionales y se combinan entre sí. Los
 * que son listas aceptan varios valores separados por coma: {@code tipoComprobante=BOLETA,FACTURA}.
 * Ej: {@code ?desde=2026-10-01&hasta=2026-10-31&metodoPago=YAPE,PLIN&porMayor=true}
 *
 * @param desde          fecha de emisión desde (inclusive), hora de Lima
 * @param hasta          fecha de emisión hasta (inclusive)
 * @param metodoPago     comprobantes con al menos un pago por alguno de esos métodos
 * @param porMayorManual true: solo los que el cajero dio por mayor sin llegar al mínimo de prendas
 * @param vendedor       usuario que hizo la venta
 * @param cajaSesionId   caja en que se cobró o se devolvió el dinero
 * @param estadoSunat    estado ante SUNAT (las notas de venta no se envían y no aparecen)
 * @param totalDesde     total del comprobante desde (inclusive)
 * @param busqueda       número (B001-15 o B001-00000015), documento o parte del nombre del cliente
 */
public record FiltroVentas(
        List<TipoComprobante> tipoComprobante,
        List<EstadoComprobante> estado,
        Long establecimientoId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        List<MetodoPago> metodoPago,
        Boolean porMayor,
        Boolean porMayorManual,
        String vendedor,
        Long clienteId,
        Long cajaSesionId,
        List<EstadoSunat> estadoSunat,
        BigDecimal totalDesde,
        BigDecimal totalHasta,
        String busqueda
) {
    /** Los mismos filtros, solo con las ventas de ese usuario. */
    public FiltroVentas delVendedor(String usuario) {
        return new FiltroVentas(tipoComprobante, estado, establecimientoId, desde, hasta, metodoPago, porMayor,
                porMayorManual, usuario, clienteId, cajaSesionId, estadoSunat, totalDesde, totalHasta, busqueda);
    }
}
