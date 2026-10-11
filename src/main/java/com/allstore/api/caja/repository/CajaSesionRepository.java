package com.allstore.api.caja.repository;

import com.allstore.api.caja.entity.CajaSesion;
import com.allstore.api.caja.entity.EstadoCaja;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CajaSesionRepository extends JpaRepository<CajaSesion, Long>, JpaSpecificationExecutor<CajaSesion> {

    @Override
    @EntityGraph(attributePaths = "establecimiento")
    Page<CajaSesion> findAll(Specification<CajaSesion> spec, Pageable pageable);

    @EntityGraph(attributePaths = "establecimiento")
    Optional<CajaSesion> findByUsuarioAndEstado(String usuario, EstadoCaja estado);

    /**
     * La caja abierta del usuario, bloqueada hasta el fin de la transacción. Ventas, devoluciones,
     * movimientos y el cierre la bloquean: así no se puede cerrar una caja mientras se está
     * registrando una venta en ella.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from CajaSesion s join fetch s.establecimiento
            where s.usuario = :usuario and s.estado = com.allstore.api.caja.entity.EstadoCaja.ABIERTA
            """)
    Optional<CajaSesion> bloquearAbierta(@Param("usuario") String usuario);
}
