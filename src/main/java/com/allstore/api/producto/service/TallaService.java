package com.allstore.api.producto.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.producto.dto.TallaRequest;
import com.allstore.api.producto.dto.TallaResponse;
import com.allstore.api.producto.entity.Talla;
import com.allstore.api.producto.repository.TallaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TallaService {

    private final TallaRepository tallaRepository;

    @Transactional(readOnly = true)
    public List<TallaResponse> listar(boolean soloActivas) {
        return tallaRepository.findAllByOrderByOrdenAscCodigoAsc().stream()
                .filter(t -> !soloActivas || t.isActivo())
                .map(TallaResponse::from)
                .toList();
    }

    @Transactional
    public TallaResponse crear(TallaRequest request) {
        String codigo = request.codigo().trim().toUpperCase();
        if (tallaRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new BusinessException("Ya existe la talla " + codigo);
        }
        Talla talla = new Talla();
        talla.setCodigo(codigo);
        talla.setOrden(request.orden());
        talla.setActivo(request.activo() == null || request.activo());
        return TallaResponse.from(tallaRepository.save(talla));
    }

    /**
     * El código de la talla ya está impreso en el SKU de las variantes existentes; cambiarlo
     * no las renombra (el SKU queda como se etiquetó), solo afecta a variantes nuevas.
     */
    @Transactional
    public TallaResponse actualizar(Long id, TallaRequest request) {
        Talla talla = tallaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Talla", id));
        String codigo = request.codigo().trim().toUpperCase();
        if (tallaRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new BusinessException("Ya existe la talla " + codigo);
        }
        talla.setCodigo(codigo);
        talla.setOrden(request.orden());
        if (request.activo() != null) {
            talla.setActivo(request.activo());
        }
        return TallaResponse.from(talla);
    }
}
