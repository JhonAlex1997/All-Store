package com.allstore.api.venta.service;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.inventario.entity.MotivoMovimiento;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.inventario.service.InventarioService;
import com.allstore.api.venta.dto.ItemDevolucionRequest;
import com.allstore.api.venta.dto.NotaCreditoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.ComprobantePago;
import com.allstore.api.venta.entity.EstadoComprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Notas de crédito: devoluciones y anulaciones de boletas y facturas. La mercadería vuelve al
 * stock del local donde se emite la nota y el dinero se devuelve desde la caja abierta.
 */
@Service
@RequiredArgsConstructor
public class NotaCreditoService {

    private final ComprobanteRepository comprobanteRepository;
    private final EmisionComprobante emision;
    private final InventarioService inventarioService;
    private final CajaService cajaService;

    @Transactional
    public VentaResponse emitir(Long comprobanteId, NotaCreditoRequest request) {
        Comprobante origen = emision.obtener(comprobanteId);
        validarOrigen(origen);
        Serie serie = emision.serieParaEmitir(request.serieId(), TipoComprobante.NOTA_CREDITO);
        // SUNAT: la serie de la nota empieza con la misma letra que el comprobante que modifica.
        if (serie.getCodigo().charAt(0) != origen.getSerie().charAt(0)) {
            throw new BusinessException("Para modificar " + origen.getNumeroImpreso() + " usa una serie de nota de crédito "
                    + "que empiece con " + origen.getSerie().charAt(0) + " (la serie " + serie.getCodigo() + " no corresponde)");
        }
        CajaSesion caja = cajaService.bloquearCajaPara(serie.getEstablecimiento().getId());
        List<Comprobante> previas = comprobanteRepository.notasCreditoDe(origen.getId());

        Comprobante nota = new Comprobante();
        nota.setTipoComprobante(TipoComprobante.NOTA_CREDITO);
        nota.setSerieRef(serie);
        nota.setEstablecimiento(serie.getEstablecimiento());
        nota.setCajaSesion(caja);
        nota.copiarClienteDe(origen);
        nota.setComprobanteOrigen(origen);
        nota.setMotivoNota(request.motivo());
        nota.setSustento(request.sustento().trim());

        List<ComprobanteDetalle> lineas = request.motivo().isTotal()
                ? lineasTotales(origen, previas, request)
                : lineasPorItem(origen, previas, request.items());
        lineas.forEach(nota::agregarDetalle);
        nota.recalcularTotales();

        ComprobantePago reembolso = new ComprobantePago();
        reembolso.setMetodo(request.metodoReembolso());
        reembolso.setMonto(nota.getTotal());
        reembolso.setReferencia(StringUtils.hasText(request.referenciaReembolso()) ? request.referenciaReembolso().trim() : null);
        nota.agregarPago(reembolso);

        List<MovimientoInventario> movimientos = inventarioService.registrar(serie.getEstablecimiento().getId(),
                MotivoMovimiento.DEVOLUCION_VENTA,
                lineas.stream().map(d -> new InventarioService.Linea(d.getVariante().getId(), d.getCantidad())).toList(),
                nota.getSustento());
        emision.emitir(nota, serie);
        emision.referenciar(movimientos, nota);
        return emision.respuesta(nota, null, List.of());
    }

    private static void validarOrigen(Comprobante origen) {
        TipoComprobante tipo = origen.getTipoComprobante();
        if (tipo == TipoComprobante.NOTA_VENTA) {
            throw new BusinessException("Una nota de venta no lleva nota de crédito: se anula, o se convierte en boleta primero");
        }
        if (tipo != TipoComprobante.BOLETA && tipo != TipoComprobante.FACTURA) {
            throw new BusinessException("Solo una boleta o una factura admite nota de crédito");
        }
        if (origen.getEstado() != EstadoComprobante.EMITIDO) {
            throw new BusinessException("El comprobante " + origen.getNumeroImpreso() + " no está vigente");
        }
    }

    /**
     * Anulación o devolución total: revierte el comprobante completo. Si ya tiene notas de
     * crédito parciales, lo que queda se devuelve por ítem (así nunca se devuelve dos veces).
     */
    private static List<ComprobanteDetalle> lineasTotales(Comprobante origen, List<Comprobante> previas,
                                                          NotaCreditoRequest request) {
        if (request.items() != null && !request.items().isEmpty()) {
            throw new BusinessException("El motivo " + request.motivo() + " revierte el comprobante completo; "
                    + "para devolver solo algunas prendas usa DEVOLUCION_POR_ITEM");
        }
        if (!previas.isEmpty()) {
            throw new BusinessException(origen.getNumeroImpreso() + " ya tiene notas de crédito ("
                    + previas.stream().map(Comprobante::getNumeroImpreso).collect(Collectors.joining(", "))
                    + "); para lo que queda usa DEVOLUCION_POR_ITEM");
        }
        return origen.getDetalles().stream().map(ComprobanteDetalle::copiar).toList();
    }

    /**
     * Devolución de algunas prendas. Cada cantidad no puede superar lo vendido menos lo ya
     * devuelto en notas anteriores. El descuento de la línea original se prorratea.
     */
    private static List<ComprobanteDetalle> lineasPorItem(Comprobante origen, List<Comprobante> previas,
                                                          List<ItemDevolucionRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("Indica las prendas y cantidades que se devuelven");
        }
        Map<Long, ComprobanteDetalle> vendidas = origen.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getVariante().getId(), Function.identity()));
        Map<Long, Integer> yaDevueltas = new HashMap<>();
        previas.forEach(nc -> nc.getDetalles().forEach(d ->
                yaDevueltas.merge(d.getVariante().getId(), d.getCantidad(), Integer::sum)));

        Set<Long> vistas = new HashSet<>();
        List<ComprobanteDetalle> lineas = new ArrayList<>();
        for (ItemDevolucionRequest item : items) {
            if (!vistas.add(item.varianteId())) {
                throw new BusinessException("Una prenda aparece más de una vez; suma las cantidades en una sola línea");
            }
            ComprobanteDetalle vendida = vendidas.get(item.varianteId());
            if (vendida == null) {
                throw new BusinessException("La prenda " + item.varianteId() + " no está en " + origen.getNumeroImpreso());
            }
            int disponible = vendida.getCantidad() - yaDevueltas.getOrDefault(item.varianteId(), 0);
            if (item.cantidad() > disponible) {
                throw new BusinessException("De " + vendida.getSku() + " solo se pueden devolver " + disponible
                        + " (vendidas " + vendida.getCantidad() + ", ya devueltas "
                        + yaDevueltas.getOrDefault(item.varianteId(), 0) + ")");
            }
            lineas.add(devolver(vendida, item.cantidad()));
        }
        return lineas;
    }

    /** Línea devuelta: mismo precio, descuento proporcional a la cantidad y su IGV recalculado. */
    private static ComprobanteDetalle devolver(ComprobanteDetalle vendida, int cantidad) {
        ComprobanteDetalle linea = vendida.copiar();
        BigDecimal descuento = vendida.getDescuento()
                .multiply(BigDecimal.valueOf(cantidad))
                .divide(BigDecimal.valueOf(vendida.getCantidad()), 2, RoundingMode.HALF_UP);
        CalculoIgv.Linea calculo = CalculoIgv.calcular(cantidad, vendida.getPrecioUnitario(), descuento,
                vendida.getTipoAfectacionIgv());
        linea.setCantidad(cantidad);
        linea.setDescuento(descuento);
        linea.setValorVenta(calculo.valorVenta());
        linea.setIgv(calculo.igv());
        linea.setTotal(calculo.total());
        return linea;
    }
}
