package com.allstore.api.produccion.repository;

import com.allstore.api.produccion.entity.EstadoOrden;
import com.allstore.api.produccion.entity.OrdenProduccion;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrdenProduccionRepository extends JpaRepository<OrdenProduccion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenProduccion o join fetch o.producto where o.id = :id")
    Optional<OrdenProduccion> bloquear(@Param("id") Long id);

    @Query(value = "select o from OrdenProduccion o join fetch o.producto where (:estado is null or o.estado = :estado)",
            countQuery = "select count(o) from OrdenProduccion o where (:estado is null or o.estado = :estado)")
    Page<OrdenProduccion> buscar(@Param("estado") EstadoOrden estado, Pageable pageable);
}
