package com.allstore.api.compra.repository;

import com.allstore.api.cliente.entity.TipoDocumentoIdentidad;
import com.allstore.api.compra.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    boolean existsByTipoDocumentoAndNumeroDocumento(TipoDocumentoIdentidad tipo, String numero);

    List<Proveedor> findAllByOrderByRazonSocialAsc();
}
