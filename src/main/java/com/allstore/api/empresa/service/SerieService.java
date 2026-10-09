package com.allstore.api.empresa.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.dto.NumeroComprobante;
import com.allstore.api.empresa.dto.SerieCreateRequest;
import com.allstore.api.empresa.dto.SerieResponse;
import com.allstore.api.empresa.dto.SerieUpdateRequest;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.entity.Serie;
import com.allstore.api.empresa.entity.TipoComprobante;
import com.allstore.api.empresa.repository.SerieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SerieService {

    private final SerieRepository serieRepository;
    private final EstablecimientoService establecimientoService;

    @Transactional(readOnly = true)
    public List<SerieResponse> listar(TipoComprobante tipo, Long establecimientoId) {
        return serieRepository.buscar(tipo, establecimientoId).stream().map(SerieResponse::from).toList();
    }

    @Transactional
    public SerieResponse crear(SerieCreateRequest request) {
        TipoComprobante tipo = request.tipoComprobante();
        String codigo = request.codigo().trim().toUpperCase();
        tipo.validarSerie(codigo).ifPresent(motivo -> {
            throw new BusinessException(motivo);
        });
        if (serieRepository.existsByTipoComprobanteAndCodigo(tipo, codigo)) {
            throw new BusinessException("Ya existe la serie " + codigo + " para " + tipo.getDescripcion().toLowerCase());
        }
        Serie serie = new Serie();
        serie.setTipoComprobante(tipo);
        serie.setCodigo(codigo);
        serie.setEstablecimiento(establecimientoActivo(request.establecimientoId()));
        serie.setCorrelativoActual(request.correlativoInicial() == null ? 0 : request.correlativoInicial());
        return SerieResponse.from(serieRepository.save(serie));
    }

    @Transactional
    public SerieResponse actualizar(Long id, SerieUpdateRequest request) {
        Serie serie = serieRepository.findByIdParaEmitir(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serie", id));
        if (!serie.getEstablecimiento().getId().equals(request.establecimientoId())) {
            serie.setEstablecimiento(establecimientoActivo(request.establecimientoId()));
        }
        if (request.correlativoActual() != null && request.correlativoActual() != serie.getCorrelativoActual()) {
            if (request.correlativoActual() < serie.getCorrelativoActual()) {
                throw new BusinessException("El correlativo no puede bajar de " + serie.getCorrelativoActual()
                        + ": se repetirían números ya emitidos");
            }
            serie.setCorrelativoActual(request.correlativoActual());
        }
        serie.setActivo(request.activo());
        return SerieResponse.from(serie);
    }

    /**
     * Asigna el siguiente número de la serie. Lo usarán ventas y facturación dentro de su propia
     * transacción (MANDATORY): si la venta falla y se revierte, el número también se revierte y
     * no quedan huecos en la numeración.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NumeroComprobante asignarSiguienteNumero(Long serieId, TipoComprobante tipoEsperado) {
        Serie serie = serieRepository.findByIdParaEmitir(serieId)
                .orElseThrow(() -> new ResourceNotFoundException("Serie", serieId));
        if (serie.getTipoComprobante() != tipoEsperado) {
            throw new BusinessException("La serie " + serie.getCodigo() + " es de "
                    + serie.getTipoComprobante().getDescripcion().toLowerCase()
                    + ", no de " + tipoEsperado.getDescripcion().toLowerCase());
        }
        long correlativo = serie.avanzarCorrelativo();
        return new NumeroComprobante(serie.getTipoComprobante(), serie.getCodigo(), correlativo);
    }

    private Establecimiento establecimientoActivo(Long id) {
        Establecimiento establecimiento = establecimientoService.obtener(id);
        if (!establecimiento.isActivo()) {
            throw new BusinessException("El establecimiento " + establecimiento.getNombre() + " está inactivo");
        }
        return establecimiento;
    }
}
