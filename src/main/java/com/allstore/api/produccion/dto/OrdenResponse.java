package com.allstore.api.produccion.dto;

import com.allstore.api.produccion.entity.Etapa;
import com.allstore.api.produccion.entity.EstadoEtapa;
import com.allstore.api.produccion.entity.EstadoOrden;
import com.allstore.api.produccion.entity.OrdenProduccion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Una orden con sus tallas, etapas e insumos.
 *
 * @param etapaActual la primera etapa que no terminó; null si todas terminaron
 * @param costoInsumos insumos consumidos a su costo (los que no tienen costo no suman); al terminar
 */
public record OrdenResponse(Long id, Long productoId, String producto, String establecimientoInsumos, String establecimientoDestino,
                            EstadoOrden estado, Etapa etapaActual, int planificadas, Integer terminadas, String observacion,
                            OffsetDateTime fechaInicio, OffsetDateTime fechaFin, BigDecimal costoInsumos, BigDecimal costoServicios,
                            BigDecimal costoUnitario, String motivoAnulacion, List<Talla> tallas, List<EtapaInfo> etapas,
                            List<Insumo> insumos) {

    public record Talla(Long varianteId, String sku, String talla, String color, int planificada, Integer terminada) {
    }

    public record EtapaInfo(Etapa etapa, String descripcion, EstadoEtapa estado, Long proveedorId, String taller,
                            OffsetDateTime fechaInicio, OffsetDateTime fechaFin, Integer unidades, BigDecimal costoServicio,
                            String observacion) {
    }

    public record Insumo(Long insumoId, String codigo, String nombre, String unidadMedida, BigDecimal cantidad, BigDecimal costo) {
    }

    public static OrdenResponse from(OrdenProduccion o) {
        Etapa actual = o.getEtapas().stream().filter(e -> e.getEstado() != EstadoEtapa.TERMINADA).map(e -> e.getEtapa())
                .findFirst().orElse(null);
        return new OrdenResponse(o.getId(), o.getProducto().getId(), o.getProducto().getCodigo() + " " + o.getProducto().getNombre(),
                o.getEstablecimientoInsumos().getNombre(), o.getEstablecimientoDestino().getNombre(), o.getEstado(), actual,
                o.planificadas(), o.getUnidadesTerminadas(), o.getObservacion(), o.getCreatedAt(), o.getFechaFin(),
                o.getCostoInsumos(), o.getCostoServicios(), o.getCostoUnitario(), o.getMotivoAnulacion(),
                o.getTallas().stream().map(t -> new Talla(t.getVariante().getId(), t.getVariante().getSku(),
                        t.getVariante().getTalla().getCodigo(), t.getVariante().getColor().getNombre(), t.getCantidadPlanificada(),
                        t.getCantidadTerminada())).toList(),
                o.getEtapas().stream().map(e -> new EtapaInfo(e.getEtapa(), e.getEtapa().getDescripcion(), e.getEstado(),
                        e.getProveedor() != null ? e.getProveedor().getId() : null,
                        e.getProveedor() != null ? e.getProveedor().getRazonSocial() : "Taller propio", e.getFechaInicio(),
                        e.getFechaFin(), e.getUnidades(), e.getCostoServicio(), e.getObservacion())).toList(),
                o.getInsumos().stream().map(i -> new Insumo(i.getInsumo().getId(), i.getInsumo().getCodigo(),
                        i.getInsumo().getNombre(), i.getInsumo().getUnidadMedida(), i.getCantidad(), i.getInsumo().getCosto())).toList());
    }
}
