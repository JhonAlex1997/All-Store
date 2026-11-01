package com.allstore.api.insumo.repository;

import com.allstore.api.insumo.entity.MovimientoInsumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoInsumoRepository extends JpaRepository<MovimientoInsumo, Long> {

    @Query(value = """
            select m from MovimientoInsumo m join fetch m.insumo
            where m.insumo.id = :insumoId and (:establecimientoId is null or m.establecimientoId = :establecimientoId)
            order by m.fecha desc, m.id desc
            """, countQuery = """
            select count(m) from MovimientoInsumo m
            where m.insumo.id = :insumoId and (:establecimientoId is null or m.establecimientoId = :establecimientoId)
            """)
    Page<MovimientoInsumo> kardex(@Param("insumoId") Long insumoId, @Param("establecimientoId") Long establecimientoId,
                                  Pageable pageable);

    List<MovimientoInsumo> findByReferenciaTipoAndReferenciaId(String referenciaTipo, Long referenciaId);
}
