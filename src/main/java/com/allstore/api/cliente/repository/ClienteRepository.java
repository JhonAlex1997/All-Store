package com.allstore.api.cliente.repository;

import com.allstore.api.cliente.entity.Cliente;
import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(TipoDocumentoIdentidad tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumentoIdentidad tipo, String numero);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdNot(TipoDocumentoIdentidad tipo, String numero, Long id);

    Optional<Cliente> findByGenericoTrue();
}
