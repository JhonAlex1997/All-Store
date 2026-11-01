package com.allstore.api.inventario.repository;

import com.allstore.api.inventario.entity.ConteoInventario;
import com.allstore.api.inventario.entity.EstadoConteo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConteoInventarioRepository extends JpaRepository<ConteoInventario, Long> {

    boolean existsByEstablecimientoIdAndEstado(Long establecimientoId, EstadoConteo estado);

    /** El conteo bloqueado: registrar cantidades y cerrar no se cruzan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConteoInventario c join fetch c.establecimiento where c.id = :id")
    Optional<ConteoInventario> bloquear(@Param("id") Long id);

    @Query(value = """
            select c from ConteoInventario c join fetch c.establecimiento e
            where (:establecimientoId is null or e.id = :establecimientoId) and (:estado is null or c.estado = :estado)
            """, countQuery = """
            select count(c) from ConteoInventario c
            where (:establecimientoId is null or c.establecimiento.id = :establecimientoId) and (:estado is null or c.estado = :estado)
            """)
    Page<ConteoInventario> buscar(@Param("establecimientoId") Long establecimientoId, @Param("estado") EstadoConteo estado,
                                  Pageable pageable);
}
