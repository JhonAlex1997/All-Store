package com.allstore.api.compra.service;

import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import com.allstore.api.compra.dto.ProveedorRequest;
import com.allstore.api.compra.dto.ProveedorResponse;
import com.allstore.api.compra.entity.Proveedor;
import com.allstore.api.compra.repository.ProveedorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listar() {
        return proveedorRepository.findAllByOrderByRazonSocialAsc().stream().map(ProveedorResponse::from).toList();
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        String numero = request.numeroDocumento().trim();
        request.tipoDocumento().validar(numero).ifPresent(motivo -> {
            throw new BusinessException(motivo);
        });
        if (proveedorRepository.existsByTipoDocumentoAndNumeroDocumento(request.tipoDocumento(), numero)) {
            throw new BusinessException("Ya existe el proveedor con " + request.tipoDocumento() + " " + numero);
        }
        Proveedor proveedor = new Proveedor();
        proveedor.setTipoDocumento(request.tipoDocumento());
        proveedor.setNumeroDocumento(numero);
        aplicar(proveedor, request);
        return ProveedorResponse.from(proveedorRepository.save(proveedor));
    }

    /** El documento no cambia (identifica al proveedor en sus compras). */
    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request) {
        Proveedor proveedor = obtener(id);
        aplicar(proveedor, request);
        return ProveedorResponse.from(proveedor);
    }

    public Proveedor obtener(Long id) {
        return proveedorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
    }

    private static void aplicar(Proveedor p, ProveedorRequest r) {
        p.setRazonSocial(r.razonSocial().trim());
        p.setDireccion(texto(r.direccion()));
        p.setTelefono(texto(r.telefono()));
        p.setEmail(StringUtils.hasText(r.email()) ? r.email().trim().toLowerCase() : null);
        p.setActivo(r.activo() == null || r.activo());
    }

    private static String texto(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
