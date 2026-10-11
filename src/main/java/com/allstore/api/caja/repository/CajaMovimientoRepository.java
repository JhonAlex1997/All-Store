package com.allstore.api.caja.repository;

import com.allstore.api.caja.entity.CajaMovimiento;
import com.allstore.api.caja.entity.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CajaMovimientoRepository extends JpaRepository<CajaMovimiento, Long> {

    List<CajaMovimiento> findByCajaSesionIdOrderById(Long cajaSesionId);

    /** Puede devolver null si no hay movimientos de ese tipo. */
    @Query("select sum(m.monto) from CajaMovimiento m where m.cajaSesion.id = :cajaId and m.tipo = :tipo")
    BigDecimal total(@Param("cajaId") Long cajaId, @Param("tipo") TipoMovimientoCaja tipo);
}
