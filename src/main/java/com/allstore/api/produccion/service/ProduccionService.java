package com.allstore.api.produccion.service;

import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.compra.service.ProveedorService;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.service.EstablecimientoService;
import com.allstore.api.insumo.entity.MotivoInsumo;
import com.allstore.api.insumo.service.InsumoService;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.produccion.dto.AnularOrdenRequest;
import com.allstore.api.produccion.dto.ConsumoRequest;
import com.allstore.api.produccion.dto.EtapaRequest;
import com.allstore.api.produccion.dto.OrdenRequest;
import com.allstore.api.produccion.dto.OrdenResponse;
import com.allstore.api.produccion.dto.TerminarOrdenRequest;
import com.allstore.api.produccion.entity.EstadoEtapa;
import com.allstore.api.produccion.entity.EstadoOrden;
import com.allstore.api.produccion.entity.Etapa;
import com.allstore.api.produccion.entity.OrdenEtapa;
import com.allstore.api.produccion.entity.OrdenInsumo;
import com.allstore.api.produccion.entity.OrdenProduccion;
import com.allstore.api.produccion.entity.OrdenTalla;
import com.allstore.api.produccion.repository.OrdenProduccionRepository;
import com.allstore.api.producto.entity.Producto;
import com.allstore.api.producto.entity.ProductoVariante;
import com.allstore.api.producto.service.ProductoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Órdenes de producción: corte, confección, lavado y acabado, en ese orden. Cada etapa se inicia
 * (en el taller propio o en uno externo) y se termina con las prendas que salieron buenas y lo que
 * cobra el taller. La orden consume insumos del local de insumos y, al terminarla, las prendas entran
 * al stock del local de destino con su costo: insumos (a su costo, si lo tienen) + servicios, entre
 * las prendas que salieron.
 */
@Service
@RequiredArgsConstructor
public class ProduccionService {

    public static final String REFERENCIA_PRODUCCION = "PRODUCCION";

    private final OrdenProduccionRepository ordenRepository;
    private final ProductoService productoService;
    private final EstablecimientoService establecimientoService;
    private final ProveedorService proveedorService;
    private final InventarioService inventarioService;
    private final InsumoService insumoService;

    @Transactional
    public OrdenResponse crear(OrdenRequest request) {
        Map<Long, ProductoVariante> variantes = productoService.obtenerVariantes(
                request.tallas().stream().map(OrdenRequest.Cantidad::varianteId).toList());
        Set<Long> vistas = new HashSet<>();
        OrdenProduccion orden = new OrdenProduccion();
        Producto producto = null;
        for (OrdenRequest.Cantidad c : request.tallas()) {
            if (!vistas.add(c.varianteId())) {
                throw new BusinessException("Una talla y color aparece más de una vez; suma las cantidades");
            }
            ProductoVariante v = variantes.get(c.varianteId());
            if (!v.getProducto().getId().equals(request.productoId())) {
                throw new BusinessException("La prenda " + v.getSku() + " no es del modelo de la orden");
            }
            producto = v.getProducto();
            OrdenTalla talla = new OrdenTalla();
            talla.setVariante(v);
            talla.setCantidadPlanificada(c.cantidad());
            orden.agregar(talla);
        }
        orden.setProducto(producto);
        orden.setEstablecimientoInsumos(establecimientoService.obtener(request.establecimientoInsumosId()));
        orden.setEstablecimientoDestino(establecimientoService.obtener(request.establecimientoDestinoId()));
        orden.setObservacion(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);
        for (Etapa etapa : Etapa.values()) {
            OrdenEtapa e = new OrdenEtapa();
            e.setEtapa(etapa);
            e.setOrdenEtapa(etapa.ordinal() + 1);
            orden.agregar(e);
        }
        return OrdenResponse.from(ordenRepository.save(orden));
    }

    /** Consume insumos del local de insumos para la orden. */
    @Transactional
    public OrdenResponse consumir(Long id, ConsumoRequest request) {
        OrdenProduccion orden = enProceso(id);
        List<InsumoService.Linea> lineas = request.items().stream().map(i -> new InsumoService.Linea(i.insumoId(), i.cantidad())).toList();
        insumoService.registrar(orden.getEstablecimientoInsumos().getId(), MotivoInsumo.CONSUMO_PRODUCCION, lineas,
                REFERENCIA_PRODUCCION, orden.getId(), documento(orden), null);
        for (InsumoService.Linea l : lineas) {
            OrdenInsumo consumo = consumido(orden, l.insumoId());
            consumo.setCantidad(consumo.getCantidad().add(l.cantidad()));
        }
        return OrdenResponse.from(orden);
    }

    /** Devuelve al local de insumos lo que sobró (nunca más de lo consumido). */
    @Transactional
    public OrdenResponse devolver(Long id, ConsumoRequest request) {
        OrdenProduccion orden = enProceso(id);
        for (ConsumoRequest.Item item : request.items()) {
            OrdenInsumo consumo = orden.getInsumos().stream().filter(i -> i.getInsumo().getId().equals(item.insumoId())).findFirst()
                    .orElseThrow(() -> new BusinessException("La orden no consumió el insumo " + item.insumoId()));
            if (item.cantidad().compareTo(consumo.getCantidad()) > 0) {
                throw new BusinessException("No se puede devolver más de lo consumido de " + consumo.getInsumo().getNombre()
                        + " (" + consumo.getCantidad().stripTrailingZeros().toPlainString() + ")");
            }
            consumo.setCantidad(consumo.getCantidad().subtract(item.cantidad()));
        }
        insumoService.registrar(orden.getEstablecimientoInsumos().getId(), MotivoInsumo.DEVOLUCION_PRODUCCION,
                request.items().stream().map(i -> new InsumoService.Linea(i.insumoId(), i.cantidad())).toList(),
                REFERENCIA_PRODUCCION, orden.getId(), documento(orden), null);
        return OrdenResponse.from(orden);
    }

    /** Inicia una etapa: la anterior debe haber terminado. */
    @Transactional
    public OrdenResponse iniciarEtapa(Long id, Etapa etapa, EtapaRequest request) {
        OrdenProduccion orden = enProceso(id);
        OrdenEtapa e = orden.etapa(etapa);
        if (e.getEstado() != EstadoEtapa.PENDIENTE) {
            throw new BusinessException("La etapa de " + etapa.getDescripcion().toLowerCase() + " ya se inició");
        }
        orden.anterior(etapa).filter(a -> a.getEstado() != EstadoEtapa.TERMINADA).ifPresent(a -> {
            throw new BusinessException("Primero termina la etapa de " + a.getEtapa().getDescripcion().toLowerCase());
        });
        e.setEstado(EstadoEtapa.EN_PROCESO);
        e.setFechaInicio(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        if (request != null && request.proveedorId() != null) {
            e.setProveedor(proveedorService.obtener(request.proveedorId()));
        }
        return OrdenResponse.from(orden);
    }

    /** Termina una etapa con las prendas que salieron buenas (no más que las que entraron) y su costo. */
    @Transactional
    public OrdenResponse terminarEtapa(Long id, Etapa etapa, EtapaRequest request) {
        OrdenProduccion orden = enProceso(id);
        OrdenEtapa e = orden.etapa(etapa);
        if (e.getEstado() != EstadoEtapa.EN_PROCESO) {
            throw new BusinessException("La etapa de " + etapa.getDescripcion().toLowerCase() + " no está en proceso");
        }
        if (request == null || request.unidades() == null) {
            throw new BusinessException("Indica cuántas prendas salieron de la etapa");
        }
        int entraron = orden.anterior(etapa).map(OrdenEtapa::getUnidades).orElse(orden.planificadas());
        if (request.unidades() > entraron) {
            throw new BusinessException("De " + etapa.getDescripcion().toLowerCase() + " no pueden salir más prendas ("
                    + request.unidades() + ") que las que entraron (" + entraron + ")");
        }
        e.setUnidades(request.unidades());
        e.setCostoServicio(request.costoServicio());
        e.setObservacion(StringUtils.hasText(request.observacion()) ? request.observacion().trim() : null);
        e.setEstado(EstadoEtapa.TERMINADA);
        e.setFechaFin(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        return OrdenResponse.from(orden);
    }

    /**
     * Cierra la orden: las prendas entran al stock del destino (motivo PRODUCCION) y se calcula su
     * costo. Requiere el acabado terminado; no entran más prendas que las que salieron del acabado.
     */
    @Transactional
    public OrdenResponse terminar(Long id, TerminarOrdenRequest request) {
        OrdenProduccion orden = enProceso(id);
        OrdenEtapa acabado = orden.etapa(Etapa.ACABADO);
        if (acabado.getEstado() != EstadoEtapa.TERMINADA) {
            throw new BusinessException("Primero termina todas las etapas (falta el acabado)");
        }
        int total = 0;
        for (TerminarOrdenRequest.Cantidad c : request.tallas()) {
            OrdenTalla talla = orden.getTallas().stream().filter(t -> t.getVariante().getId().equals(c.varianteId())).findFirst()
                    .orElseThrow(() -> new BusinessException("La prenda " + c.varianteId() + " no es de esta orden"));
            talla.setCantidadTerminada(c.cantidad());
            total += c.cantidad();
        }
        if (total == 0) {
            throw new BusinessException("Indica cuántas prendas salieron");
        }
        if (total > acabado.getUnidades()) {
            throw new BusinessException("Entran " + total + " prendas pero del acabado salieron " + acabado.getUnidades());
        }
        BigDecimal insumos = orden.getInsumos().stream()
                .filter(i -> i.getInsumo().getCosto() != null)
                .map(i -> i.getCantidad().multiply(i.getInsumo().getCosto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal servicios = orden.getEtapas().stream().map(OrdenEtapa::getCostoServicio).filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitario = insumos.add(servicios).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        orden.setCostoInsumos(insumos);
        orden.setCostoServicios(servicios);
        orden.setCostoUnitario(unitario);
        orden.setUnidadesTerminadas(total);

        List<InventarioService.Linea> lineas = orden.getTallas().stream()
                .filter(t -> t.getCantidadTerminada() != null && t.getCantidadTerminada() > 0)
                .map(t -> new InventarioService.Linea(t.getVariante().getId(), t.getCantidadTerminada())).toList();
        inventarioService.registrar(orden.getEstablecimientoDestino().getId(), MotivoMovimiento.PRODUCCION, lineas, null)
                .forEach(m -> m.referenciar(REFERENCIA_PRODUCCION, orden.getId(), documento(orden)));
        if (request.actualizarCostos() && unitario.signum() > 0) {
            orden.getTallas().stream().filter(t -> t.getCantidadTerminada() != null && t.getCantidadTerminada() > 0)
                    .forEach(t -> t.getVariante().setCosto(unitario.setScale(2, RoundingMode.HALF_UP)));
        }
        orden.setEstado(EstadoOrden.TERMINADA);
        orden.setFechaFin(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        return OrdenResponse.from(orden);
    }

    /**
     * Anula la orden. En proceso: vuelven al local los insumos consumidos. Terminada: salen del stock
     * las prendas que entraron (si ya se vendieron, no se puede).
     */
    @Transactional
    public OrdenResponse anular(Long id, AnularOrdenRequest request) {
        OrdenProduccion orden = bloquear(id);
        String motivo = request.motivo().trim();
        switch (orden.getEstado()) {
            case ANULADA -> throw new BusinessException("La orden " + id + " ya está anulada");
            case EN_PROCESO -> {
                List<InsumoService.Linea> devolver = orden.getInsumos().stream().filter(i -> i.getCantidad().signum() > 0)
                        .map(i -> new InsumoService.Linea(i.getInsumo().getId(), i.getCantidad())).toList();
                if (!devolver.isEmpty()) {
                    insumoService.registrar(orden.getEstablecimientoInsumos().getId(), MotivoInsumo.DEVOLUCION_PRODUCCION,
                            devolver, REFERENCIA_PRODUCCION, orden.getId(), "Anulación " + documento(orden).toLowerCase(), motivo);
                    orden.getInsumos().forEach(i -> i.setCantidad(BigDecimal.ZERO));
                }
            }
            case TERMINADA -> {
                List<InventarioService.Linea> sacar = orden.getTallas().stream()
                        .filter(t -> t.getCantidadTerminada() != null && t.getCantidadTerminada() > 0)
                        .map(t -> new InventarioService.Linea(t.getVariante().getId(), t.getCantidadTerminada())).toList();
                inventarioService.registrar(orden.getEstablecimientoDestino().getId(), MotivoMovimiento.ANULACION_PRODUCCION,
                        sacar, motivo).forEach(m -> m.referenciar(REFERENCIA_PRODUCCION, orden.getId(),
                        "Anulación " + documento(orden).toLowerCase()));
            }
        }
        orden.setEstado(EstadoOrden.ANULADA);
        orden.setMotivoAnulacion(motivo);
        return OrdenResponse.from(orden);
    }

    @Transactional(readOnly = true)
    public OrdenResponse obtener(Long id) {
        return OrdenResponse.from(ordenRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrdenResponse> listar(EstadoOrden estado, Pageable pageable) {
        return PageResponse.of(ordenRepository.buscar(estado, pageable).map(OrdenResponse::from));
    }

    private OrdenInsumo consumido(OrdenProduccion orden, Long insumoId) {
        return orden.getInsumos().stream().filter(i -> i.getInsumo().getId().equals(insumoId)).findFirst().orElseGet(() -> {
            OrdenInsumo nuevo = new OrdenInsumo();
            nuevo.setInsumo(insumoService.obtener(insumoId));
            orden.agregar(nuevo);
            return nuevo;
        });
    }

    private OrdenProduccion enProceso(Long id) {
        OrdenProduccion orden = bloquear(id);
        if (orden.getEstado() != EstadoOrden.EN_PROCESO) {
            throw new BusinessException("La orden " + id + " ya está " + orden.getEstado().name().toLowerCase().replace('_', ' '));
        }
        return orden;
    }

    private OrdenProduccion bloquear(Long id) {
        return ordenRepository.bloquear(id).orElseThrow(() -> new ResourceNotFoundException("Orden de producción", id));
    }

    private static String documento(OrdenProduccion orden) {
        return "Orden de producción N° " + orden.getId();
    }
}
