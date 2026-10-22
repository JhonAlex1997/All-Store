package com.allstore.api.facturacion.repository;

import com.allstore.api.facturacion.entity.EnvioSunat;
import com.allstore.api.facturacion.entity.EstadoSunat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EnvioSunatRepository extends JpaRepository<EnvioSunat, Long> {

    Optional<EnvioSunat> findByComprobanteId(Long comprobanteId);

    Optional<EnvioSunat> findByResumenId(Long resumenId);

    Optional<EnvioSunat> findByGuiaId(Long guiaId);

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
