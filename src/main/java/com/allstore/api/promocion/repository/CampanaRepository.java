package com.allstore.api.promocion.repository;

import com.allstore.api.promocion.entity.Campana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CampanaRepository extends JpaRepository<Campana, Long> {

    /** Activas ese día (en cualquier local; el local se filtra después), con su alcance. */
    @Query("""
            select distinct c from Campana c left join fetch c.alcance left join fetch c.establecimiento
            where c.activo and c.fechaInicio <= :dia and c.fechaFin >= :dia
            """)
    List<Campana> vigentes(@Param("dia") LocalDate dia);

    @Query("select distinct c from Campana c left join fetch c.alcance left join fetch c.establecimiento order by c.fechaInicio desc, c.id desc")
    List<Campana> todas();
}
