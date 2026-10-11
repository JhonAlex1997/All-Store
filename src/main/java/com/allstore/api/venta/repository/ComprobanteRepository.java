package com.allstore.api.venta.repository;

import com.allstore.api.venta.entity.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long>, JpaSpecificationExecutor<Comprobante> {

    /** El comprobante que se emitió a partir de otro (ej. la boleta de una nota de venta). */
    Optional<Comprobante> findFirstByComprobanteOrigenIdOrderByIdDesc(Long comprobanteOrigenId);
}
