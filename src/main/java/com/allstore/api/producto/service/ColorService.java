package com.allstore.api.producto.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.producto.dto.ColorRequest;
import com.allstore.api.producto.dto.ColorResponse;
import com.allstore.api.producto.entity.Color;
import com.allstore.api.producto.repository.ColorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ColorService {

    private final ColorRepository colorRepository;

    @Transactional(readOnly = true)
    public List<ColorResponse> listar(boolean soloActivos) {
        return colorRepository.findAllByOrderByNombreAsc().stream()
                .filter(c -> !soloActivos || c.isActivo())
                .map(ColorResponse::from)
                .toList();
    }

    @Transactional
    public ColorResponse crear(ColorRequest request) {
        String nombre = request.nombre().trim();
        String codigo = request.codigo().trim().toUpperCase();
        if (colorRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe el color " + nombre);
        }
        if (colorRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new BusinessException("La abreviatura " + codigo + " ya la usa otro color");
        }
        Color color = new Color();
        color.setNombre(nombre);
        color.setCodigo(codigo);
        color.setActivo(request.activo() == null || request.activo());
        return ColorResponse.from(colorRepository.save(color));
    }

    /** Igual que en tallas: cambiar la abreviatura no renombra los SKU ya generados. */
    @Transactional
    public ColorResponse actualizar(Long id, ColorRequest request) {
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Color", id));
        String nombre = request.nombre().trim();
        String codigo = request.codigo().trim().toUpperCase();
        if (colorRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BusinessException("Ya existe el color " + nombre);
        }
        if (colorRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new BusinessException("La abreviatura " + codigo + " ya la usa otro color");
        }
        color.setNombre(nombre);
        color.setCodigo(codigo);
        if (request.activo() != null) {
            color.setActivo(request.activo());
        }
        return ColorResponse.from(color);
    }
}
