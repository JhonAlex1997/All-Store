package com.allstore.api.venta.service;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.service.CajaService;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.venta.dto.ConceptoNotaDebitoRequest;
import com.allstore.api.venta.dto.NotaDebitoRequest;
import com.allstore.api.venta.dto.VentaResponse;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.entity.ComprobanteDetalle;
import com.allstore.api.venta.entity.EstadoComprobante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Notas de débito: cobran un monto adicional sobre una boleta o factura (intereses por mora,
 * penalidades o un aumento en el valor). No mueven mercadería: cada línea es un concepto. El
 * cobro entra a la caja abierta, como una venta.
 */
@Service
@RequiredArgsConstructor
public class NotaDebitoService {

    /** Catálogo 03: "ZZ" = servicio (la línea no es una prenda). */
    static final String UNIDAD_SERVICIO = "ZZ";

    private final EmisionComprobante emision;
    private final CajaService cajaService;

    @Transactional
    public VentaResponse emitir(Long comprobanteId, NotaDebitoRequest request) {
        Comprobante origen = emision.obtener(comprobanteId);
        validarOrigen(origen);
        Serie serie = emision.serieParaEmitir(request.serieId(), TipoComprobante.NOTA_DEBITO);
        // SUNAT: la serie de la nota empieza con la misma letra que el comprobante que modifica.
        if (serie.getCodigo().charAt(0) != origen.getSerie().charAt(0)) {
            throw new BusinessException("Para modificar " + origen.getNumeroImpreso() + " usa una serie de nota de débito "
                    + "que empiece con " + origen.getSerie().charAt(0) + " (la serie " + serie.getCodigo() + " no corresponde)");
        }
        CajaSesion caja = cajaService.bloquearCajaPara(serie.getEstablecimiento().getId());

        Comprobante nota = new Comprobante();
        nota.setTipoComprobante(TipoComprobante.NOTA_DEBITO);
        nota.setSerieRef(serie);
        nota.setEstablecimiento(serie.getEstablecimiento());
        nota.setCajaSesion(caja);
        nota.copiarClienteDe(origen);
        nota.setComprobanteOrigen(origen);
        nota.setMotivoNotaDebito(request.motivo());
        nota.setSustento(request.sustento().trim());
        request.conceptos().forEach(c -> nota.agregarDetalle(linea(c)));
        nota.recalcularTotales();
        emision.agregarPagos(nota, request.pagos());

        emision.emitir(nota, serie);
        return emision.respuesta(nota, null, List.of());
    }

    private static void validarOrigen(Comprobante origen) {
        TipoComprobante tipo = origen.getTipoComprobante();
        if (tipo != TipoComprobante.BOLETA && tipo != TipoComprobante.FACTURA) {
            throw new BusinessException("Solo una boleta o una factura admite nota de débito"
                    + (tipo == TipoComprobante.NOTA_VENTA ? "; convierte primero la nota de venta" : ""));
        }
        if (origen.getEstado() != EstadoComprobante.EMITIDO) {
            throw new BusinessException("El comprobante " + origen.getNumeroImpreso() + " no está vigente");
        }
    }

    /** Un concepto: cantidad 1, su monto como precio y el IGV separado según su afectación. */
    private static ComprobanteDetalle linea(ConceptoNotaDebitoRequest concepto) {
        String afectacion = concepto.tipoAfectacionIgv() != null ? concepto.tipoAfectacionIgv() : CalculoIgv.AFECTACION_GRAVADO;
        CalculoIgv.Linea calculo = CalculoIgv.calcular(1, concepto.monto(), BigDecimal.ZERO, afectacion);
        ComprobanteDetalle detalle = new ComprobanteDetalle();
        detalle.setDescripcion(concepto.descripcion().trim());
        detalle.setUnidadMedida(UNIDAD_SERVICIO);
        detalle.setTipoAfectacionIgv(afectacion);
        detalle.setCantidad(1);
        detalle.setPrecioUnitario(concepto.monto().setScale(2));
        detalle.setDescuento(BigDecimal.ZERO.setScale(2));
        detalle.setValorVenta(calculo.valorVenta());
        detalle.setIgv(calculo.igv());
        detalle.setTotal(calculo.total());
        return detalle;
    }
}
