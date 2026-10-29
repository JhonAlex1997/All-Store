package com.allstore.api.entrega.repository;

import com.allstore.api.entrega.entity.EnvioCorreo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EnvioCorreoRepository extends JpaRepository<EnvioCorreo, Long> {

    List<EnvioCorreo> findByComprobanteIdOrderByIdDesc(Long comprobanteId);

    boolean existsByComprobanteIdAndAutomaticoTrue(Long comprobanteId);

    /** Correos pedidos por usuarios (no automáticos) de un comprobante desde ese momento. */
    long countByComprobanteIdAndAutomaticoFalseAndCreatedAtAfter(Long comprobanteId, OffsetDateTime desde);

    /** Pendientes cuyo próximo intento ya llegó, los más antiguos primero. */
    @Query("""
            select e.id from EnvioCorreo e
            where e.estado = com.allstore.api.entrega.entity.EstadoCorreo.PENDIENTE
              and (e.proximoIntento is null or e.proximoIntento <= :ahora)
            order by e.id
            """)
    List<Long> idsPorEnviar(@Param("ahora") OffsetDateTime ahora, Pageable limite);

    /** Lo toma para enviarlo, saltándolo si otra instancia ya lo está enviando (nadie recibe dos veces el mismo correo). */
    @Query(value = "select * from envio_correo where id = :id for update skip locked", nativeQuery = true)
    Optional<EnvioCorreo> tomarParaEnviar(@Param("id") Long id);
}
