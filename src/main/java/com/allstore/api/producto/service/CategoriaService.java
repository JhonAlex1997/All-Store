package com.allstore.api.producto.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.producto.dto.CategoriaRequest;
import com.allstore.api.producto.dto.CategoriaResponse;
import com.allstore.api.producto.entity.Categoria;
import com.allstore.api.producto.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(boolean soloActivas) {
        return categoriaRepository.findAllByOrderByNombreAsc().stream()
                .filter(c -> !soloActivas || c.isActivo())
                .map(CategoriaResponse::from)
                .toList();
    }

    @Transactional
    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe la categoría " + nombre);
        }
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setActivo(request.activo() == null || request.activo());
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = obtener(id);
        String nombre = request.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new BusinessException("Ya existe la categoría " + nombre);
        }
        categoria.setNombre(nombre);
        if (request.activo() != null) {
            categoria.setActivo(request.activo());
        }
        return CategoriaResponse.from(categoria);
    }

    Categoria obtener(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }
}
