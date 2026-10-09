package com.allstore.api.empresa.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.empresa.dto.EstablecimientoRequest;
import com.allstore.api.empresa.dto.EstablecimientoResponse;
import com.allstore.api.empresa.entity.Establecimiento;
import com.allstore.api.empresa.repository.EstablecimientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EstablecimientoService {

    private final EstablecimientoRepository establecimientoRepository;

    @Transactional(readOnly = true)
    public List<EstablecimientoResponse> listar(boolean soloActivos) {
        return establecimientoRepository.findAllByOrderByCodigoAsc().stream()
                .filter(e -> !soloActivos || e.isActivo())
                .map(EstablecimientoResponse::from)
                .toList();
    }

    @Transactional
    public EstablecimientoResponse crear(EstablecimientoRequest request) {
        if (establecimientoRepository.existsByCodigo(request.codigo())) {
            throw new BusinessException("Ya existe el establecimiento con código " + request.codigo());
        }
        Establecimiento establecimiento = new Establecimiento();
        aplicar(establecimiento, request);
        establecimiento.setActivo(request.activo() == null || request.activo());
        return EstablecimientoResponse.from(establecimientoRepository.save(establecimiento));
    }

    @Transactional
    public EstablecimientoResponse actualizar(Long id, EstablecimientoRequest request) {
        Establecimiento establecimiento = obtener(id);
        if (establecimientoRepository.existsByCodigoAndIdNot(request.codigo(), id)) {
            throw new BusinessException("Ya existe el establecimiento con código " + request.codigo());
        }
        aplicar(establecimiento, request);
        if (request.activo() != null) {
            establecimiento.setActivo(request.activo());
        }
        return EstablecimientoResponse.from(establecimiento);
    }

    Establecimiento obtener(Long id) {
        return establecimientoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", id));
    }

    private void aplicar(Establecimiento establecimiento, EstablecimientoRequest request) {
        establecimiento.setCodigo(request.codigo());
        establecimiento.setNombre(request.nombre().trim());
        establecimiento.setTipo(request.tipo());
        establecimiento.setDireccion(request.direccion().trim());
        establecimiento.setUbigeo(request.ubigeo());
    }
}
