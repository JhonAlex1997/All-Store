package com.allstore.api.cliente.service;

import com.allstore.api.cliente.dto.ClienteRequest;
import com.allstore.api.cliente.dto.ClienteResponse;
import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.cliente.repository.ClienteRepository;
import com.allstore.api.common.dto.PageResponse;
import com.allstore.api.common.exception.BusinessException;
import com.allstore.api.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;

    /** Busca por nombre o número de documento. */
    @Transactional(readOnly = true)
    public PageResponse<ClienteResponse> listar(String busqueda, TipoDocumentoIdentidad tipoDocumento,
                                                Boolean activo, Pageable pageable) {
        Specification<Cliente> spec = Specification.unrestricted();
        if (StringUtils.hasText(busqueda)) {
            String patron = "%" + busqueda.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(root.get("numeroDocumento"), patron)));
        }
        if (tipoDocumento != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipoDocumento"), tipoDocumento));
        }
        if (activo != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("activo"), activo));
        }
        return PageResponse.of(clienteRepository.findAll(spec, pageable).map(ClienteResponse::from));
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtenerDetalle(Long id) {
        return ClienteResponse.from(obtener(id));
    }

    /** Para la caja: el cajero digita el DNI o RUC y obtiene al cliente si ya existe. */
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorDocumento(TipoDocumentoIdentidad tipo, String numero) {
        return clienteRepository.findByTipoDocumentoAndNumeroDocumento(tipo, normalizarNumero(numero))
                .map(ClienteResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay un cliente registrado con " + tipo.getDescripcion() + " " + numero));
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtenerGenerico() {
        return clienteRepository.findByGenericoTrue()
                .map(ClienteResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No está configurado el cliente genérico"));
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest request) {
        TipoDocumentoIdentidad tipo = request.tipoDocumento();
        String numero = normalizarNumero(request.numeroDocumento());
        validarDocumento(tipo, numero);
        if (clienteRepository.existsByTipoDocumentoAndNumeroDocumento(tipo, numero)) {
            throw new BusinessException("Ya existe un cliente con " + tipo.getDescripcion() + " " + numero);
        }
        Cliente cliente = new Cliente();
        cliente.setTipoDocumento(tipo);
        cliente.setNumeroDocumento(numero);
        aplicarDatos(cliente, request);
        cliente.setActivo(request.activo() == null || request.activo());
        return ClienteResponse.from(clienteRepository.save(cliente));
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest request) {
        Cliente cliente = obtener(id);
        if (cliente.isGenerico()) {
            throw new BusinessException("El cliente genérico \"Clientes varios\" no se puede modificar");
        }
        TipoDocumentoIdentidad tipo = request.tipoDocumento();
        String numero = normalizarNumero(request.numeroDocumento());
        validarDocumento(tipo, numero);
        if (clienteRepository.existsByTipoDocumentoAndNumeroDocumentoAndIdNot(tipo, numero, id)) {
            throw new BusinessException("Ya existe otro cliente con " + tipo.getDescripcion() + " " + numero);
        }
        cliente.setTipoDocumento(tipo);
        cliente.setNumeroDocumento(numero);
        aplicarDatos(cliente, request);
        if (request.activo() != null) {
            cliente.setActivo(request.activo());
        }
        return ClienteResponse.from(cliente);
    }

    private void validarDocumento(TipoDocumentoIdentidad tipo, String numero) {
        if (tipo == TipoDocumentoIdentidad.SIN_DOCUMENTO) {
            throw new BusinessException("Para ventas sin identificar al comprador usa el cliente \"Clientes varios\"");
        }
        tipo.validar(numero).ifPresent(motivo -> {
            throw new BusinessException(motivo);
        });
    }

    private void aplicarDatos(Cliente cliente, ClienteRequest request) {
        cliente.setNombre(request.nombre().trim().replaceAll("\\s+", " "));
        cliente.setDireccion(limpiar(request.direccion()));
        cliente.setUbigeo(limpiar(request.ubigeo()));
        cliente.setTelefono(limpiar(request.telefono()));
        cliente.setEmail(limpiar(request.email()) == null ? null : request.email().trim().toLowerCase());
    }

    private Cliente obtener(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
    }

    private static String normalizarNumero(String numero) {
        return numero.trim().toUpperCase();
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
