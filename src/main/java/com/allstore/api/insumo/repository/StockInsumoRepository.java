package com.allstore.api.insumo.repository;

import com.allstore.api.insumo.entity.StockInsumo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockInsumoRepository extends JpaRepository<StockInsumo, Long> {

    /** El saldo bloqueado hasta el fin de la transacción (dos consumos no restan a la vez). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from StockInsumo s join fetch s.insumo join fetch s.establecimiento
            where s.insumo.id = :insumoId and s.establecimiento.id = :establecimientoId
            """)
    Optional<StockInsumo> bloquear(@Param("insumoId") Long insumoId, @Param("establecimientoId") Long establecimientoId);

    @Query("""
            select s from StockInsumo s join fetch s.insumo i join fetch s.establecimiento e
            where (:establecimientoId is null or e.id = :establecimientoId) order by i.nombre, e.nombre
            """)
    List<StockInsumo> listar(@Param("establecimientoId") Long establecimientoId);
}
