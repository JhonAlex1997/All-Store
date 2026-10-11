package com.allstore.api.venta.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.config.JpaAuditingConfig;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.service.SerieService;
import com.allstore.api.inventario.entity.MovimientoInventario;
import com.allstore.api.venta.entity.Comprobante;
import com.allstore.api.venta.repository.ComprobanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pasos comunes a todo comprobante (venta, conversión, nota de crédito): validar la serie,
 * tomar el número, guardar y enlazar los movimientos de inventario con el documento.
 */
@Component
@RequiredArgsConstructor
public class EmisionComprobante {

    public static final String REFERENCIA_COMPROBANTE = "COMPROBANTE";

    private final ComprobanteRepository comprobanteRepository;
    private final SerieService serieService;

    /** La serie debe ser del tipo pedido y estar activa, igual que su establecimiento. */
    public Serie serieParaEmitir(Long serieId, TipoComprobante tipo) {
        Serie serie = serieService.obtener(serieId);
        if (serie.getTipoComprobante() != tipo) {
            throw new BusinessException("La serie " + serie.getCodigo() + " es de "
                    + serie.getTipoComprobante().getDescripcion().toLowerCase() + ", no de "
                    + tipo.getDescripcion().toLowerCase());
        }
        if (!serie.isActivo()) {
            throw new BusinessException("La serie " + serie.getCodigo() + " está inactiva");
        }
        if (!serie.getEstablecimiento().isActivo()) {
            throw new BusinessException("El establecimiento de la serie " + serie.getCodigo() + " está inactivo");
        }
        return serie;
    }

    /** Toma el número de la serie (bloqueándola) y guarda el comprobante. Va al final de la transacción. */
    public void emitir(Comprobante comprobante, Serie serie) {
        NumeroComprobante numero = serieService.asignarSiguienteNumero(serie.getId(), comprobante.getTipoComprobante());
        comprobante.setSerie(numero.serie());
        comprobante.setCorrelativo(numero.correlativo());
        comprobante.setFechaEmision(OffsetDateTime.now(JpaAuditingConfig.ZONA_LIMA));
        comprobanteRepository.save(comprobante);
    }

    public void referenciar(List<MovimientoInventario> movimientos, Comprobante comprobante) {
        String documento = comprobante.getTipoComprobante().getDescripcion() + " " + comprobante.getNumeroImpreso();
        movimientos.forEach(m -> m.referenciar(REFERENCIA_COMPROBANTE, comprobante.getId(), documento));
    }

    public Comprobante obtener(Long id) {
        return comprobanteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comprobante", id));
    }
}
