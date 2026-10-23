package com.allstore.api.facturacion.repository;

import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnvioSunatRepository extends JpaRepository<EnvioSunat, Long> {

    Optional<EnvioSunat> findByComprobanteId(Long comprobanteId);

    // ---------- Para las alertas ----------

    /** Comprobantes en ese estado de SUNAT que siguen vigentes en el sistema (ej. rechazados sin reemitir ni anular). */
    @Query(value = """
            select e from EnvioSunat e join fetch e.comprobante c
            where e.estado in :estados and c.estado = com.allstore.api.venta.entity.EstadoComprobante.EMITIDO
              and c.fechaEmision < :emitidosAntesDe
            order by c.fechaEmision
            """, countQuery = """
            select count(e) from EnvioSunat e join e.comprobante c
            where e.estado in :estados and c.estado = com.allstore.api.venta.entity.EstadoComprobante.EMITIDO
              and c.fechaEmision < :emitidosAntesDe
            """)
    Page<EnvioSunat> comprobantesVigentes(@Param("estados") Collection<EstadoSunat> estados,
                                          @Param("emitidosAntesDe") OffsetDateTime emitidosAntesDe, Pageable limite);

    /** Comunicaciones de baja y resúmenes diarios en ese estado. */
    @Query(value = "select e from EnvioSunat e join fetch e.resumen where e.estado = :estado order by e.id",
            countQuery = "select count(e) from EnvioSunat e where e.resumen is not null and e.estado = :estado")
    Page<EnvioSunat> resumenes(@Param("estado") EstadoSunat estado, Pageable limite);

    /** Guías en esos estados cuyo traslado empieza hasta esa fecha. */
    @Query(value = """
            select e from EnvioSunat e join fetch e.guia g
            where e.estado in :estados and g.fechaTraslado <= :hasta order by g.fechaTraslado
            """, countQuery = """
            select count(e) from EnvioSunat e join e.guia g where e.estado in :estados and g.fechaTraslado <= :hasta
            """)
    Page<EnvioSunat> guiasPorTrasladar(@Param("estados") Collection<EstadoSunat> estados, @Param("hasta") LocalDate hasta,
                                       Pageable limite);

    Optional<EnvioSunat> findByResumenId(Long resumenId);

    Optional<EnvioSunat> findByGuiaId(Long guiaId);

    @Query("select e from EnvioSunat e where e.comprobante.id in :comprobanteIds")
    List<EnvioSunat> deComprobantes(@Param("comprobanteIds") Collection<Long> comprobanteIds);

    /**
     * Por enviar (PENDIENTE) o por consultar su ticket (EN_PROCESO), cuyo próximo intento ya
     * llegó, los más antiguos primero.
     */
    @Query("""
            select e.id from EnvioSunat e
            where e.estado in (com.allstore.api.facturacion.entity.EstadoSunat.PENDIENTE,
                               com.allstore.api.facturacion.entity.EstadoSunat.EN_PROCESO)
              and (e.proximoIntento is null or e.proximoIntento <= :ahora)
            order by e.id
            """)
    List<Long> idsPorEnviar(@Param("ahora") OffsetDateTime ahora, Pageable limite);

    /**
     * Toma el envío para procesarlo, saltándolo si otra instancia ya lo está procesando
     * (FOR UPDATE SKIP LOCKED): con varios servidores, ningún comprobante se envía dos veces.
     */
    @Query(value = "select * from envio_sunat where id = :id for update skip locked", nativeQuery = true)
    Optional<EnvioSunat> tomarParaProcesar(@Param("id") Long id);

    @EntityGraph(attributePaths = {"comprobante", "resumen", "guia"})
    Page<EnvioSunat> findByEstado(EstadoSunat estado, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"comprobante", "resumen", "guia"})
    Page<EnvioSunat> findAll(Pageable pageable);
}
