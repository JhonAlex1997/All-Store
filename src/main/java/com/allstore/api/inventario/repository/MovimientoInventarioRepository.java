package com.allstore.api.inventario.repository;

import com.allstore.api.inventario.entity.MovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    /** Kardex: del más reciente al más antiguo. */
    @Query(value = """
            select m from MovimientoInventario m
            join fetch m.variante v join fetch m.establecimiento e
            where (:varianteId is null or v.id = :varianteId)
              and (:establecimientoId is null or e.id = :establecimientoId)
              and m.createdAt >= :desde and m.createdAt < :hasta
            order by m.id desc
            """,
            countQuery = """
            select count(m) from MovimientoInventario m
            where (:varianteId is null or m.variante.id = :varianteId)
              and (:establecimientoId is null or m.establecimiento.id = :establecimientoId)
              and m.createdAt >= :desde and m.createdAt < :hasta
            """)
    Page<MovimientoInventario> kardex(@Param("varianteId") Long varianteId,
                                      @Param("establecimientoId") Long establecimientoId,
                                      @Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta,
                                      Pageable pageable);

    List<MovimientoInventario> findByReferenciaTipoAndReferenciaId(String referenciaTipo, Long referenciaId);
}
