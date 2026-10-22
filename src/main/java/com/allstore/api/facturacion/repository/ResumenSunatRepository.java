package com.allstore.api.facturacion.repository;

import com.allstore.api.facturacion.entity.ResumenSunat;
import com.allstore.api.facturacion.entity.ResumenSunatDetalle;
import com.allstore.api.facturacion.entity.TipoResumen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ResumenSunatRepository extends JpaRepository<ResumenSunat, Long> {

    /**
     * Bloqueo de la numeración de resúmenes hasta que termine la transacción: si dos usuarios
     * anulan a la vez, el segundo espera y toma el correlativo siguiente (sin él, ambos
     * calcularían el mismo número).
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext('resumen_sunat'))", nativeQuery = true)
    Integer bloquearNumeracion();

    /** Correlativo para un nuevo resumen de ese tipo y día (empieza en 1 cada día). */
    @Query("""
            select coalesce(max(r.correlativo), 0) + 1 from ResumenSunat r
            where r.tipo = :tipo and r.fechaGeneracion = :fecha
            """)
    int siguienteCorrelativo(@Param("tipo") TipoResumen tipo, @Param("fecha") LocalDate fecha);

    /** La línea más reciente que incluye al comprobante: su baja ante SUNAT. */
    @Query("""
            select d from ResumenSunatDetalle d join fetch d.resumen
            where d.comprobante.id = :comprobanteId order by d.id desc limit 1
            """)
    Optional<ResumenSunatDetalle> ultimaBaja(@Param("comprobanteId") Long comprobanteId);
}
